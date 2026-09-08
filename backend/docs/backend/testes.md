# Testes do backend

Como o backend do Pitaco é testado, por que dessa forma, e a receita de cada tipo
de teste — o que escrever, com qual ferramenta, e onde parar.

Complementa a [arquitetura](arquitetura.md): lá está como o código é organizado;
aqui, como se prova que ele funciona.

---

## 1. Duas decisões que explicam o resto

### Fake no lugar de mock

**Mock sobre uma porta do projeto é proibido.** Toda porta tem uma implementação de
teste real em `testsupport/` — `InMemoryApplicationRepository`, `DirectTransactor`.

Um mock afirma o que você *acha* que o colaborador faz. Um fake **é** um colaborador
que faz. A diferença aparece na primeira mudança de contrato: o mock continua verde
enquanto o sistema quebra, porque ele foi programado para devolver o que você
esperava em outubro.

E há um ganho de graça: o fake obriga o contrato da porta a ser implementável duas
vezes. Uma porta que só o Spring Data consegue cumprir é uma porta mal desenhada, e
escrever o fake é o que revela isso.

> **A única exceção**: tipo de biblioteca de terceiro cujo comportamento não é do
> projeto. `ApiExceptionHandlerTest` mocka `Tracer` do Micrometer, porque construir
> um tracer real só para checar se o `traceId` aparece na resposta custaria mais do
> que vale. Mockar a **sua** porta é fugir do contrato; mockar a fronteira de uma
> biblioteca é economizar setup.

### O teste afirma sobre `type` e `code`, nunca sobre a mensagem

```java
assertThatThrownBy(() -> Slug.of("Acme"))
    .isInstanceOf(DomainException.class)
    .satisfies(error -> {
      var domainError = (DomainException) error;
      assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
      assertThat(domainError.code()).isEqualTo("application.slug_invalid");
    });
```

`code` é contrato público e não muda. Mensagem é texto para humano e melhora com o
tempo. Um teste que afirma sobre mensagem transforma cada melhoria de texto em
build vermelho — e, pior, ensina o time a não melhorar o texto.

**A exceção é o E2E**, que verifica se a mensagem certa chega ao campo certo do
`$.errors` — porque ali o que está sendo testado é justamente o contrato de que a
mensagem do value object é a que o cliente vê.

---

## 2. Os seis tipos de teste

A suíte tem 110 testes hoje. Nem todos são "unitário" ou "E2E" — há uma escala.

| # | Tipo | Contexto Spring? | Ferramenta | Custo |
|---|---|---|---|---|
| 1 | **Value object / entidade** | não | JUnit + AssertJ | ~1ms |
| 2 | **Caso de uso** | não | JUnit + fake in-memory | ~2ms |
| 3 | **DTO / validação** | não | `Validation.buildDefaultValidatorFactory()` | ~2ms |
| 4 | **Componente de borda** | não | MockMvc `standaloneSetup` | ~50ms |
| 5 | **Adaptador** | não | fake do colaborador de framework | ~1ms |
| 6 | **E2E** | sim | `@E2E` + Testcontainers + `RestTestClient` | segundos |

Os cinco primeiros rodam sem Docker e sem contexto Spring. Só o tipo 6 exige
Docker rodando.

**A regra de escolha:** use o tipo mais barato que consegue provar o que você
precisa provar. E2E é para o contrato HTTP ponta a ponta, não para varrer
combinação de entrada — isso é trabalho do tipo 1 ou 3.

---

## 3. O ferramental de `testsupport`

```
src/test/java/.../testsupport
├── annotations/E2E.java                    # a anotação composta de E2E
├── database/DatabaseCleaner.java           # truncate entre testes
├── factories/ApplicationFactory.java       # builder fluente de dados
├── repositories/InMemoryApplicationRepository.java
└── transaction/DirectTransactor.java
```

### `@E2E`

Anotação composta que carrega tudo de que um teste de ponta a ponta precisa:

```java
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
@AutoConfigureRestTestClient
@SpringBootTest(classes = PitacoApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface E2E {}
```

Dois detalhes que valem entender antes de mexer:

- **`classes = PitacoApplication.class` é explícito** porque a classe de boot mora
  em `infra`, e a busca ascendente por `@SpringBootConfiguration` não a encontra a
  partir dos testes sob `modules`.
- **Não há `@Transactional`.** Com `RANDOM_PORT` o servidor atende em outra thread,
  então a transação do teste não enxergaria o commit do caso de uso — o teste
  passaria a testar a própria transação em vez do sistema. A limpeza é explícita.

### `DatabaseCleaner`

Descobre as tabelas pelo `information_schema` e faz `truncate ... restart identity
cascade`. O teste não precisa conhecer a ordem das chaves estrangeiras, e tabela
nova entra de graça. Chame no `@BeforeEach`:

```java
@Autowired DatabaseCleaner database;

@BeforeEach
void setUp() { database.clean(); }
```

### Factory de dados

Builder fluente com um default sensato e um nome que se lê:

```java
ApplicationFactory.anApplication().build()
ApplicationFactory.anApplicationWithPolicies().build()
ApplicationFactory.anApplication().withName("Outro").withSlug("outro").build()
ApplicationFactory.anApplication().buildSavedIn(applications)   // já persistido
ApplicationFactory.anApplication().asCreateInput()              // como Input do use case
```

**Construção manual repetida entre testes é bug esperando.** Quando a entidade
ganha um campo obrigatório, com factory você muda um arquivo; sem ela, trinta.

Ao criar a factory de um agregado novo, ofereça os quatro caminhos: `build()`,
`buildSavedIn(repo)`, `as<Uso>Input()` e um atalho para a variante mais comum.

### Fakes de porta

`InMemoryApplicationRepository` guarda num `LinkedHashMap` — ordem de inserção
preservada, o que torna `containsExactly` previsível. `DirectTransactor` executa
direto e conta invocações, para o teste poder afirmar que o trabalho passou por uma
transação.

---

## 4. Convenções

**Nome de classe:** `<Alvo>Test`, e `<Alvo>E2ETest` para os de ponta a ponta.
Visibilidade package-private (`class`, sem `public`).

**Nome de método:** frase em português, snake_case, descrevendo o comportamento —
`rejeita_texto_fora_do_formato`, `nao_deve_criar_uma_aplicacao_quando_ja_existir_uma_com_mesmo_slug`.

**`@DisplayName`** onde o nome do método não basta. Na classe, o alvo
(`"POST /applications"`, `"Application"`, `"Create Application sut"`); no método, o
comportamento esperado em português corrente
(`"Slug já usado responde 409 sem criar uma segunda aplicação"`).

**O sujeito do teste chama-se `sut`** quando a classe testa um objeto com estado
montado no `@BeforeEach`:

```java
private ApplicationRepository applications;
private CreateApplicationUseCase sut;

@BeforeEach
void setUp() {
  applications = new InMemoryApplicationRepository();
  sut = new CreateApplicationUseCase(applications);
}
```

**Asserção é AssertJ** — `assertThat`, `assertThatThrownBy`. Encadeie em vez de
repetir o sujeito. Nada de `assertTrue(a.equals(b))`: é `assertThat(a).isEqualTo(b)`,
que dá mensagem de falha útil.

**`@ParameterizedTest` para famílias de entrada.** É o que faz `SlugTest` ter 30
casos sem ter 30 métodos:

```java
@ParameterizedTest
@ValueSource(strings = {"acme", "acme-app", "acme-app-2", "a1"})
void aceita_minusculas_digitos_e_hifen_entre_termos(String valid) { ... }

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"   "})
void rejeita_slug_ausente(String invalid) { ... }

@ParameterizedTest
@CsvSource({"Acme App, acme-app", "Pitaço Café, pitaco-cafe"})
void deriva_slug_do_texto(String text, String expected) { ... }
```

`@NullAndEmptySource` + `@ValueSource(strings = "   ")` é o trio padrão para
"ausente" — null, vazio e branco.

**Estrutura do corpo:** montar, agir, afirmar, separados por linha em branco. Sem
comentários `// given` — a linha em branco já diz.

---

## 5. Receita — value object

O teste mais barato e o mais denso. Cobre **cada** caminho de rejeição, porque é
aqui que a invariante vive.

```java
class TitleTest {

  @ParameterizedTest
  @ValueSource(strings = {"NPS", "Pesquisa trimestral", "a"})
  void aceita_texto_dentro_do_limite(String valid) {
    assertThat(Title.of(valid).value()).isEqualTo(valid);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_titulo_ausente(String invalid) {
    assertThatThrownBy(() -> Title.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(error -> {
          var domainError = (DomainException) error;
          assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
          assertThat(domainError.code()).isEqualTo("survey.title_invalid");
        });
  }

  @Test
  void rejeita_titulo_longo_demais() {
    assertThat(Title.of("a".repeat(200)).value()).hasSize(200);       // a borda que passa
    assertThatThrownBy(() -> Title.of("a".repeat(201)))               // a que não passa
        .isInstanceOf(DomainException.class);
  }

  @Test
  void o_texto_e_como_o_titulo_aparece() {
    assertThat(Title.of("NPS")).hasToString("NPS");
  }
}
```

**Sempre teste os dois lados do limite** no mesmo método — 200 passa, 201 falha.
Um teste que só checa 201 não prova que o limite é 200.

**Cubra:** cada formato aceito, cada formato rejeitado, ausência (null/vazio/branco),
os dois lados de cada limite numérico, `toString()`, e a derivação se houver
(`Slug.from`) — incluindo o caso em que ela é impossível.

## 6. Receita — entidade

Aqui o que se testa é **comportamento e transição de estado**, não os getters.

```java
@DisplayName("Survey")
class SurveyTest {

  static final Title TITLE = Title.of("NPS trimestral");

  static Survey newSurvey() {
    return SurveyFactory.aSurvey().withTitle(TITLE.value()).build();
  }

  /**
   * O carimbo não é escolhido pelo teste: o que dá para afirmar é que ele foi
   * refeito depois do instante em que a mutação começou — o que falha se
   * touch() não for chamado, porque aí ele continua no createdAt.
   */
  static void assertTouchedSince(Survey survey, Instant before) {
    assertThat(survey.getUpdatedAt()).isAfterOrEqualTo(before);
  }

  @Test
  @DisplayName("nasce com identidade própria e updatedAt igual ao createdAt")
  void nasce_intacta() {
    var survey = newSurvey();

    assertThat(survey.id()).isNotNull();
    assertThat(survey.getTitle()).isEqualTo(TITLE);
    assertThat(survey.getUpdatedAt()).isEqualTo(survey.getCreatedAt());
  }

  @Test
  @DisplayName("renomear troca o título e marca a edição")
  void renomear_marca_a_edicao() {
    var survey = newSurvey();
    var before = Instant.now();

    survey.rename(Title.of("NPS anual"));

    assertThat(survey.getTitle()).isEqualTo(Title.of("NPS anual"));
    assertTouchedSince(survey, before);
  }
}
```

**Cubra:** o estado ao nascer, cada método de negócio (efeito + `touch()`), cada
invariante que a entidade guarda, e o `restore` preservando o que veio do banco.

Para a base compartilhada (`Entity`, `Id`), o teste declara tipos de mentira no
próprio arquivo — veja `EntityTest`, que cria `Answer` e `Question` só para provar
que entidades de tipos diferentes com o mesmo id não são iguais.

## 7. Receita — caso de uso

Fake in-memory, nunca mock. O teste afirma sobre **o estado que sobrou no
repositório**, não sobre "o método foi chamado".

```java
@DisplayName("Create Survey sut")
class CreateSurveyUseCaseTest {

  private SurveyRepository surveys;
  private CreateSurveyUseCase sut;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    sut = new CreateSurveyUseCase(surveys);
  }

  @Test
  @DisplayName("Deve criar e persistir uma nova pesquisa")
  void deve_criar_uma_pesquisa() {
    var output = sut.execute(SurveyFactory.aSurvey().asCreateInput());

    var created = surveys.findById(SurveyId.of(output.id())).orElseThrow();

    assertThat(created.getTitle().value()).isEqualTo("NPS trimestral");
  }

  @Test
  @DisplayName("Não deve criar quando já existir uma com o mesmo título")
  void nao_deve_criar_quando_ja_existir() {
    var existing = SurveyFactory.aSurvey().buildSavedIn(surveys);

    assertThatThrownBy(() -> sut.execute(SurveyFactory.aSurvey().asCreateInput()))
        .isInstanceOf(SurveyAlreadyExists.class)
        .extracting(error -> ((SurveyAlreadyExists) error).title())
        .isEqualTo(existing.getTitle());

    assertThat(surveys.findAll()).containsExactly(existing);   // nada foi criado
  }
}
```

Duas coisas para copiar daqui:

- **`.extracting(...)` para afirmar sobre o dado do erro nomeado** — é isso que
  justifica o erro carregar o dado em vez de só a mensagem.
- **O caminho de falha também afirma que nada mudou.** `containsExactly(existing)`
  prova que a rejeição não deixou lixo. Sem essa linha o teste passaria mesmo se o
  caso de uso salvasse antes de checar.

**Cubra:** o caminho feliz completo, cada variação de entrada que muda o resultado
(campo opcional ausente, derivação de valor), e cada erro que o caso de uso lança —
sempre verificando que o estado ficou intacto.

## 8. Receita — DTO e validação

Roda o Bean Validation sozinho, sem contexto Spring:

```java
class CreateSurveyRequestDTOTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void startValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() { factory.close(); }

  private Map<String, String> violationsOf(CreateSurveyRequestDTO request) {
    return validator.validate(request).stream()
        .collect(Collectors.toMap(
            violation -> violation.getPropertyPath().toString(),
            ConstraintViolation::getMessage,
            (first, second) -> first));
  }

  @Test
  void rejeita_titulo_longo_demais() {
    assertThat(violationsOf(requestWith("a".repeat(201))))
        .containsEntry("title", "Título não pode passar de 200 caracteres");
  }

  @Test
  void aponta_todos_os_campos_invalidos_de_uma_vez() {
    assertThat(violationsOf(new CreateSurveyRequestDTO("")))
        .containsOnlyKeys("title");
  }

  @Test
  void converte_para_a_entrada_do_caso_de_uso() {
    var input = new CreateSurveyRequestDTO("NPS").toInput();

    assertThat(input.title()).isEqualTo("NPS");
  }
}
```

**Este é o único lugar onde se afirma sobre mensagem** fora do E2E — e de propósito:
o que está sendo testado é justamente que a mensagem do DTO espelha a do value
object. Se as duas divergirem, o cliente vê texto diferente conforme onde o erro
parou, e este teste é o que impede isso.

**Cubra:** o payload mínimo válido, o completo, cada constraint violada com a
mensagem exata, o caso em que todos os campos falham de uma vez, e o `toInput()`.

## 9. Receita — componente de borda

Para um `@RestControllerAdvice` ou um componente HTTP isolado, `MockMvc` em
`standaloneSetup` dá o comportamento real do Spring MVC sem subir contexto — 50ms em
vez de segundos. O truque é declarar um **controller de mentira** no próprio teste,
que lança exatamente o que se quer traduzir:

```java
class ApiExceptionHandlerTest {

  @RestController
  static class TestController {
    @GetMapping("/answers/not-found")
    String notFound() {
      throw new NotFoundException("answer.not_found", "Resposta 42 não encontrada");
    }

    @GetMapping("/answers/boom")
    String boom() {
      throw new IllegalStateException("conexão jdbc://user:senha@host falhou");
    }
  }

  private MockMvc mockMvcWith(Optional<Tracer> tracer) {
    return MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(new ApiExceptionHandler(tracer))
        .build();
  }

  @Test
  void erro_inesperado_vira_500_sem_vazar_mensagem_interna() throws Exception {
    mockMvcWithoutTracing()
        .perform(get("/answers/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("internal.unexpected"))
        .andExpect(content().string(not(containsString("senha"))));
  }
}
```

O último `andExpect` é o modelo de **teste de segurança**: não basta afirmar o que a
resposta contém, é preciso afirmar o que ela **não pode** conter. A senha estava na
mensagem da exceção; o teste prova que ela não chega ao cliente.

## 10. Receita — adaptador

Quando o adaptador integra um colaborador de framework, escreva um fake que
**registra o que aconteceu** e afirme a sequência:

```java
private static final class RecordingTransactionManager implements PlatformTransactionManager {
  private final List<String> events = new ArrayList<>();

  @Override public TransactionStatus getTransaction(TransactionDefinition d) {
    events.add("begin");
    return new SimpleTransactionStatus();
  }
  @Override public void commit(TransactionStatus s) { events.add("commit"); }
  @Override public void rollback(TransactionStatus s) { events.add("rollback"); }
}

@Test
@DisplayName("Deve fazer rollback e preservar o tipo do erro quando o trabalho falha")
void deve_fazer_rollback_preservando_o_erro() {
  var failure = new ConflictException("app.duplicated", "Já existe");

  assertThatThrownBy(() -> sut.inTransaction(() -> { throw failure; }))
      .isSameAs(failure);

  assertThat(transactionManager.events).containsExactly("begin", "rollback");
}
```

`isSameAs` e não `isInstanceOf`: o teste prova que o adaptador **não embrulha** a
exceção — o tipo original chega intacto à borda, que é o que faz o `ErrorType`
sobreviver ao caminho.

## 11. Receita — E2E

O único tipo que sobe a aplicação inteira: Tomcat em porta aleatória, Postgres
e stack LGTM via Testcontainers. **Docker precisa estar rodando.** Nenhum
serviço externo é necessário.

```java
@E2E
@DisplayName("POST /surveys")
class CreateSurveyE2ETest {

  @Autowired RestTestClient client;
  @Autowired SurveyJpaRepository surveys;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() { database.clean(); }

  @Test
  @DisplayName("Cria a pesquisa, devolve 201 com Location e persiste")
  void cria_uma_pesquisa() {
    var request = new CreateSurveyRequestDTO("NPS trimestral");

    var response = client.post().uri("/surveys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody(CreateSurveyResponseDTO.class)
        .returnResult();

    var body = response.getResponseBody();

    assertThat(body).isNotNull();
    assertThat(body.id()).isNotBlank();
    assertThat(response.getResponseHeaders().getLocation())
        .asString().endsWith("/surveys/" + body.id());

    var saved = surveys.findById(body.id()).orElseThrow();      // o estado no banco

    assertThat(saved.getTitle()).isEqualTo("NPS trimestral");
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("Título já usado responde 409 sem criar uma segunda pesquisa")
  void recusa_titulo_duplicado() {
    // ... cria a primeira ...

    client.post().uri("/surveys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO("NPS trimestral"))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code").isEqualTo("survey.already_exists")
        .jsonPath("$.detail").value(String.class,
            detail -> assertThat(detail).contains("NPS trimestral"));

    assertThat(surveys.count()).isEqualTo(1);      // nada foi criado
  }

  @Test
  @DisplayName("Payload inválido responde 400 detalhando cada campo")
  void recusa_payload_invalido() {
    client.post().uri("/surveys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO("  "))
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code").isEqualTo("request.invalid")
        .jsonPath("$.errors.title").isEqualTo("Título é obrigatório");

    assertThat(surveys.count()).isZero();
  }

  @Test
  @DisplayName("JSON malformado responde 400 sem vazar o erro de parsing")
  void recusa_json_malformado() {
    client.post().uri("/surveys")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"title\":}")
        .exchange()
        .expectStatus().isBadRequest();
  }
}
```

### O mínimo obrigatório de um endpoint

Um endpoint **só é considerado pronto** com E2E cobrindo:

- [ ] **caminho feliz** — status, corpo, header `Location` quando aplicável, **e o
      estado persistido** lido de volta do banco
- [ ] **rejeição de validação** — 400 com `$.code = "request.invalid"` e cada campo
      em `$.errors`
- [ ] **cada erro de negócio anunciado no OpenAPI** — 409, 422, 404: status, `$.code`
      e o `Content-Type` `application/problem+json`
- [ ] **JSON malformado** — 400 sem vazar erro de parsing
- [ ] **o estado não mudou** em todo caminho de falha (`count()` zero ou inalterado)

Verificar o banco depois da resposta é o que separa um E2E de um teste de
serialização. A resposta pode estar perfeita e a escrita ter ido para o lugar
errado.

### Duas armadilhas

**Não use `@Transactional` num teste E2E.** Com `RANDOM_PORT` o servidor atende em
outra thread; a transação do teste não vê o commit da aplicação, e o rollback
automático não desfaz o que ela escreveu. Use `DatabaseCleaner` no `@BeforeEach`.

**Não varra combinação de entrada em E2E.** Cada caso custa segundos e sobe a
aplicação inteira para provar algo que um teste de value object prova em 1ms. E2E
prova que as peças se encaixam; que cada peça funciona é assunto dos outros tipos.

### Precisando de um controller só para o teste

`ContextPathTest` faz isso para provar que o `context-path` vale para rotas de
aplicação e também para o ferramental:

```java
@E2E
@Import(ContextPathTest.PingControllerConfig.class)
class ContextPathTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class PingControllerConfig {
    @RestController
    static class PingController {
      @GetMapping("/ping") String ping() { return "pong"; }
    }
  }
}
```

---

## 12. Escrevendo na ordem certa

Toda regra de negócio nasce com um teste que falha antes de existir o código que a
satisfaz. Para uma funcionalidade nova:

| # | Passo | Teste que vem antes |
|---|---|---|
| 1 | value objects e entidade | tipo 1 — invariantes |
| 2 | porta de repositório + fake | — |
| 3 | caso de uso | tipo 2 — sobre o fake |
| 4 | migration + adaptador JPA | — (coberto pelo E2E) |
| 5 | DTO de entrada | tipo 3 — constraints e `toInput` |
| 6 | presenter, Swagger, controller | — |
| 7 | endpoint completo | tipo 6 — E2E |

Correção de bug entra com o teste que o reproduz, escrito **antes** da correção. Um
teste escrito depois prova que o código atual faz o que faz; escrito antes, prova
que o bug existia.

---

## 13. Rodando

```bash
./mvnw test                                     # suíte completa (110 testes)
./mvnw verify                                   # testes + empacotamento
./mvnw test -Dtest=SlugTest                     # uma classe
./mvnw test -Dtest='*E2ETest'                   # só os E2E de endpoint
```

### Rodando sem Docker

Quatro classes sobem contexto Spring com Testcontainers e exigem Docker:
`CreateApplicationE2ETest`, `ContextPathTest`, `OpenApiConfigTest` e
`PitacoApplicationTests`. As outras nove rodam em Java puro.

Como as três últimas não compartilham o sufixo `E2ETest`, **`-Dtest='!*E2ETest'`
não basta** — ele ainda sobe Docker. Hoje o filtro tem de nomeá-las:

```bash
./mvnw test -Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'
# 99 testes, nenhum container
```

> Isso é atrito, e a correção é pequena: pôr `@Tag("e2e")` na anotação `@E2E` e em
> `PitacoApplicationTests` faria `./mvnw test -DexcludedGroups=e2e` funcionar sem
> lista de nomes. Ainda não foi feito.

A JVM de teste roda com `-Duser.timezone=UTC`, o mesmo fuso de produção — nada de
teste que passa na sua máquina e falha no CI porque um `Instant` mudou de dia.

Testcontainers reaproveita os containers entre classes E2E da mesma execução; a
primeira classe paga o start, as seguintes não.

---

## 14. Checklist de review

- [ ] Nenhum mock sobre porta do projeto — fake in-memory em `testsupport`
- [ ] Asserção de erro sobre `type` e `code`, não sobre mensagem (exceto DTO e E2E)
- [ ] Dado vindo de factory, não de construção manual repetida
- [ ] `@DisplayName` na classe e nos métodos cujo nome não basta
- [ ] Família de entrada em `@ParameterizedTest`, não em métodos copiados
- [ ] Os dois lados de cada limite testados
- [ ] Caminho de falha afirma que o estado não mudou
- [ ] E2E cobre feliz + validação + cada erro do OpenAPI + JSON malformado
- [ ] E2E lê o banco de volta; não confia só na resposta
- [ ] Nenhum `@Transactional` em E2E
- [ ] Nenhum teste `@Disabled` sem justificativa escrita
- [ ] `./mvnw verify` verde
