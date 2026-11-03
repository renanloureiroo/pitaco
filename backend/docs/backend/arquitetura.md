# Arquitetura do backend

Como o código do backend do Pitaco é organizado, por que ele é assim, e o passo a
passo para escrever coisa nova sem quebrar o padrão.

Este documento descreve o que **existe**. O que ainda é intenção está marcado
explicitamente como tal. As regras aqui são a forma operacional da
[constituição do projeto](../../.specify/memory/constitution.md); os ADRs em
[`../adrs`](../adrs) registram as decisões que levaram até aqui.

---

## 1. O mapa

O código é dividido em dois mundos, e a dependência aponta em uma única direção.

```
┌─────────────────────────────────────────────────────────┐
│  infra          Spring, HTTP, JPA, Swagger, Testcontainers│
│                 sabe tudo sobre o mundo lá fora           │
└───────────────────────────┬─────────────────────────────┘
                            │ depende de
                            ▼
┌─────────────────────────────────────────────────────────┐
│  core + domain  Java puro. Não sabe que Spring existe.   │
│                 Não sabe que HTTP existe.                 │
└─────────────────────────────────────────────────────────┘
```

A seta nunca se inverte. É a única regra da qual todas as outras derivam, e a mais
fácil de verificar: abra qualquer arquivo em `core/` ou em `modules/*/domain/` e
procure por `import org.springframework`. Se achar, o PR está errado.

```
com.renanloureiroo.pitaco
├── core                          # o que vale para o sistema inteiro
│   ├── entity/Entity.java        # base de identidade das entidades
│   ├── error/                    # ApplicationException, ErrorType, famílias
│   ├── identity/Id.java          # base tipada de identificador de agregado
│   ├── transaction/Transactor    # porta de transação
│   └── usecase/                  # os quatro contratos de caso de uso
│
├── infra                         # o que é detalhe, para o sistema inteiro
│   ├── PitacoApplication.java
│   ├── http/config/              # OpenAPI
│   ├── http/error/               # ApiExceptionHandler, ErrorTypeHttpStatus
│   └── transaction/              # SpringTransactor
│
└── modules
    └── app                       # um módulo por contexto de negócio
        ├── domain/               # entidades, value objects, enums de estado
        ├── application/          # casos de uso, portas, erros nomeados
        └── infra/                # controllers, DTOs, JPA, cabeamento Spring
```

**`core` é para o que qualquer módulo usa.** Um tipo só sobe para `core` quando o
segundo módulo precisa dele. `Slug` mora no módulo `app` e não em `core`, ainda que
"slug" pareça genérico: hoje só uma aplicação tem slug.

---

## 2. As peças do núcleo

### `Id` — identificador opaco e tipado

Todo identificador de agregado estende `Id`. Duas propriedades importam:

- **Opaco.** O valor é um texto. Que hoje ele seja UUID é detalhe interno, não
  contrato. Trocar para ULID amanhã não muda nenhuma assinatura.
- **Tipado.** A igualdade compara o tipo concreto além do valor. Um `ApplicationId`
  nunca é igual a um `UserId`, nem em compilação nem em runtime — mesmo carregando o
  mesmo texto.

```java
public final class ApplicationId extends Id {
  private ApplicationId(String value) { super(value); }

  public static ApplicationId generate() { return new ApplicationId(newValue()); }
  public static ApplicationId of(String value) { return new ApplicationId(value); }
}
```

Sempre dois construtores nomeados: `generate()` para o identificador novo, `of()`
para o que vem de fora (banco, URL, payload). Construtor privado.

### `Entity` — identidade, não conteúdo

`Entity<ID extends Id>` iguala por tipo concreto + identificador. Dois `Application`
com o mesmo `ApplicationId` são a mesma aplicação, ainda que um esteja editado e o
outro seja uma leitura mais antiga do banco.

Para o que se define pelo conteúdo, use `record`. A igualdade estrutural já vem
pronta e não há base a herdar.

> **A pergunta que decide:** se dois objetos com todos os atributos iguais forem
> coisas diferentes, é entidade. Se forem a mesma coisa, é value object.
>
> Duas aplicações chamadas "Acme App" são aplicações diferentes → entidade.
> Dois slugs `acme-app` são o mesmo slug → value object.

Não existe `AggregateRoot`. Sem eventos de domínio, ele não teria nada além do que
`Entity` já dá. Entra no dia em que houver evento para acumular.

### `ApplicationException` e `ErrorType` — erro que não sabe o que é HTTP

Todo erro do sistema estende `ApplicationException` e carrega dois dados:

| Campo | O que é | Quem consome |
|---|---|---|
| `type` | a natureza do erro, independente de protocolo | o adapter, para escolher o status |
| `code` | identificador textual estável, `<contexto>.<motivo>` | o cliente e o suporte |

O `code` é contrato público — o cliente se orienta por ele. A **mensagem é livre**
e pode mudar a qualquer momento; por isso teste nenhum afirma sobre mensagem.

Os seis `ErrorType` e para onde a borda HTTP os traduz:

| `ErrorType` | Significado | HTTP |
|---|---|---|
| `NOT_FOUND` | o recurso não existe | 404 |
| `CONFLICT` | o estado atual impede a operação (duplicidade, concorrência) | 409 |
| `VALIDATION` | entrada malformada ou fora do formato | 400 |
| `UNAUTHORIZED` | identidade ausente ou não comprovada | 401 |
| `FORBIDDEN` | identidade conhecida, sem permissão | 403 |
| `BUSINESS_RULE` | invariante de domínio ou regra de negócio violada | 422 |

A tradução vive em `ErrorTypeHttpStatus`, na borda. **Um caso de uso que precisa de
um status novo cria um `ErrorType` novo — nunca um atalho na borda.**

A hierarquia: `DomainException` (default `BUSINESS_RULE`, aceita outro tipo),
`NotFoundException`, `ConflictException`, `UnauthorizedException`,
`ForbiddenException`. Erro recorrente de um agregado vira classe nomeada:

```java
public final class ApplicationAlreadyExistsWithSameSlug extends ConflictException {
  private static final String CODE = "application.already_exists_with_same_slug";

  private final Slug slug;

  public ApplicationAlreadyExistsWithSameSlug(Slug slug) {
    super(CODE, "Já existe uma aplicação com o slug \"" + slug + "\"");
    this.slug = slug;
  }

  /** O slug que colidiu — é o que a borda precisa devolver a quem tentou criar. */
  public Slug slug() { return slug; }
}
```

O erro nomeado carrega o **dado**, não só a mensagem. Quem trata do outro lado não
precisa fazer parsing de texto.

### `UseCase` — uma interface funcional, um `execute`

Quatro variações conforme a assinatura:

```java
UseCase<I, O>                    O execute(I input)
UseCaseWithoutInput<O>           O execute()
UseCaseWithoutOutput<I>          void execute(I input)
UseCaseWithoutInputAndOutput     void execute()
```

`Input` e `Output` são `record` aninhados na própria classe do caso de uso. Isso
mantém o contrato junto de quem o cumpre e evita um pacote de DTOs anêmicos.

### `Transactor` — porta de transação

```java
public interface Transactor {
  <T> T inTransaction(Supplier<T> work);
  default void runInTransaction(Runnable work) { ... }
}
```

Existe implementada (`SpringTransactor`) e já em uso: o caso de uso declara a
`@Transactional` **do projeto** (`core.transaction`), e quem a aplica é o
`TransactionalAdvisor`. O primeiro caso concreto foi o `RevokeApiKeyUseCase` — não por
duas escritas, mas por uma leitura e uma escrita que precisam do mesmo instante do banco.

### Presenter — não é porta, e não mora em `core`

A montagem da resposta é feita por uma `final class` com construtor privado e um
`public static R present(O output)`, em `modules/<módulo>/infra/http/presenters`. Não
existe interface `Presenter` e não existe bean: é o mesmo molde de `ApiKeyJpaMapper`,
porque é a mesma natureza — tradução pura, sem estado nem dependência.

Houve uma versão anterior em que `Presenter<O, R>` era interface em `core/presenter`,
descrita como porta. Foi removida na constituição 1.1.0: porta existe para inverter
dependência, e essa não invertia nenhuma — quem declarava, implementava e consumia o
presenter era `infra`. Ver a seção 6.4.

---

## 3. Anatomia de um módulo

Um módulo é um contexto de negócio. Ele tem as três camadas, e a dependência entre
elas também é unidirecional: `infra` → `application` → `domain`.

```
modules/app
├── domain                                # Java puro. O coração.
│   ├── entities/Application.java         # a entidade, com as invariantes
│   ├── entities/ApplicationId.java
│   ├── entities/Status.java              # enum de estado
│   └── valueobjects/{Name,Slug}.java     # records auto-validados
│
├── application                           # Java puro. Orquestra o domínio.
│   ├── usecases/CreateApplicationUseCase.java
│   ├── repositories/ApplicationRepository.java    # PORTA
│   └── errors/ApplicationAlreadyExistsWithSameSlug.java
│
└── infra                                 # Spring vive aqui, e só aqui.
    ├── config/UseCasesConfiguration.java          # cabeamento
    ├── http/controllers/ApplicationController.java
    ├── http/controllers/ApplicationControllerSwagger.java
    ├── http/dtos/CreateApplication{Request,Response}DTO.java
    └── database/jpa/
        ├── entities/ApplicationJpaEntity.java     # o modelo do banco
        ├── mappers/ApplicationJpaMapper.java      # domínio ↔ banco
        ├── repositories/ApplicationJpaRepository.java   # Spring Data
        └── repositories/ApplicationRepositoryJpa.java   # ADAPTADOR
```

### O par porta/adaptador de persistência

Quatro arquivos, e cada um tem uma razão de existir:

1. **`ApplicationRepository`** (porta, em `application`) — fala a linguagem do
   domínio: recebe `Slug`, devolve `Optional<Application>`. Não sabe o que é JPA.
2. **`ApplicationJpaEntity`** (em `infra`) — o modelo do banco, com anotações
   `jakarta.persistence`. Colunas, tipos, `nullable`, `unique`.
3. **`ApplicationJpaMapper`** — classe final com construtor privado e dois métodos
   estáticos, `toJpa` e `toDomain`. É onde `Application.restore(...)` é chamado.
4. **`ApplicationRepositoryJpa`** (adaptador) — implementa a porta usando o
   `ApplicationJpaRepository` do Spring Data e o mapper.

> **Por que uma entidade JPA separada da entidade de domínio?** Porque a entidade de
> domínio tem construtor privado, invariantes no construtor e nenhum setter — e o
> Hibernate precisa de construtor sem argumento e de setters. Fundir as duas
> significaria abrir a entidade de domínio para que o ORM a preencha, e aí a
> invariante deixa de ser garantida.

`Application.restore(...)` existe exatamente para isso: reconstrói a entidade a
partir do que já está no banco, sem regerar identidade nem carimbos de tempo.
`create(...)` é para o que nasce agora. **Nunca chame `create` num mapper.**

---

## 4. Uma requisição, ponta a ponta

`POST /api/applications`:

```
 1. Tomcat                    context-path /api é do contêiner; o controller
                              declara só @RequestMapping("/applications")
        │
 2. CreateApplicationRequestDTO
        │                     record com Bean Validation. Falhou aqui?
        │                     → MethodArgumentNotValidException
        │                     → ApiExceptionHandler → 400 com $.errors
        ▼
 3. ApplicationController     chama request.toInput() e delega. Sem lógica.
        │
        ▼
 4. CreateApplicationUseCase  Java puro:
        │                       - Slug.of(...) ou Slug.from(nome)
        │                       - checa duplicidade pela porta
        │                       - Application.create(...)  ← invariantes aqui
        │                       - persiste pela porta
        │                       - log.info do sucesso
        ▼
 5. ApplicationRepositoryJpa  mapper → Spring Data save() → Postgres
        │
        ▼
 6. resposta                  201 + header Location + corpo
```

E quando algo dá errado no passo 4:

```
   DomainException / ApplicationAlreadyExistsWithSameSlug
        │  sobe sem ser capturada por ninguém no caminho
        ▼
   ApiExceptionHandler        ponto único de tradução
        │  ErrorTypeHttpStatus.of(type) → status
        │  corpo RFC 9457 (application/problem+json) + code + traceId
        │  log com nível derivado do ErrorType
        ▼
   409 / 422 / 404 ...
```

**`try/catch` traduzindo erro em controller é proibido.** O handler é o único lugar
que converte exceção em resposta. Um `catch` no meio do caminho quebra a garantia de
que toda resposta de erro da API tem o mesmo formato.

### O formato de erro

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Já existe uma aplicação com o slug \"acme-app\"",
  "instance": "/api/applications",
  "code": "application.already_exists_with_same_slug",
  "traceId": "8f3a1c2b9d4e5f60"
}
```

- `code` — estável, é por ele que o cliente se orienta.
- `traceId` — liga a resposta ao trace no Grafana. Sai só com tracing ligado.
- `errors` — acrescentado nos erros de Bean Validation: mapa campo → mensagem.

Erro inesperado nunca vaza mensagem interna: vira `internal.unexpected` genérico,
com o stack trace apenas no log. Há teste garantindo que uma senha presente na
mensagem de uma exceção não aparece no corpo da resposta.

### O log de erro acontece uma única vez

Na borda, com nível derivado do `ErrorType`:

| `ErrorType` | Nível | Por quê |
|---|---|---|
| `NOT_FOUND`, `VALIDATION` | `DEBUG` | ruído em volume; só interessa a quem investiga |
| `CONFLICT`, `BUSINESS_RULE` | `INFO` | conta algo sobre como o sistema é usado |
| `UNAUTHORIZED`, `FORBIDDEN` | `WARN` | pode ser abuso; precisa aparecer sem ninguém pedir |
| inesperado | `ERROR` | com stack trace |

**Caso de uso loga sucesso, não erro.** E entrada do usuário nunca vai para o log:
registra-se o campo e o motivo, jamais o valor rejeitado.

---

## 5. As regras, e o que cada uma custa

| Regra | O que ela compra | O que ela custa |
|---|---|---|
| Sem Spring no `core` | trocar framework não toca em regra de negócio | cabeamento manual em `@Configuration` |
| Sem `@Service` em caso de uso | o caso de uso é testável com `new` | uma linha de `@Bean` por caso de uso |
| Entidade JPA separada | a invariante do domínio é inviolável | um mapper por agregado |
| `code` estável, mensagem livre | mensagem melhora sem quebrar cliente | disciplina de nomear código |
| Erro sem HTTP | somar gRPC não mexe no domínio | um `ErrorType` a manter |
| `Id` tipado | `UserId` no lugar de `AppId` não compila | uma classe por agregado |
| Validação no construtor | tipo em mãos é tipo válido | não dá para construir "parcialmente" |
| Repositório via Spring Data | sem boilerplate de `EntityManager` | preso ao Spring Data em `infra` |

### Convenções que não cabem em tabela

- **Rotas não repetem o prefixo.** `/api` vem do `context-path`. Escreva
  `@RequestMapping("/applications")`.
- **Migrations** ficam em `src/main/resources/db/migration`, no padrão
  `V<yyyyMMddHHmmss>__descricao.sql` com timestamp **em UTC** — duas branches nunca
  disputam o mesmo número. `ddl-auto` é `validate`: o schema nasce da migration,
  nunca do Hibernate. **Migration aplicada não se edita**; correção é migration nova.
- **Tempo** é `Instant`, gravado e lido em UTC. A JVM roda com
  `-Duser.timezone=UTC` em execução e em teste. Fuso é assunto da apresentação.
- **Log** sempre via `@Slf4j` do Lombok. `LoggerFactory` explícito é proibido.
- **Javadoc documenta a decisão, não a assinatura.** Se o comentário só repete o
  nome do método, ele não precisa existir. Regra não óbvia vira teste, não parágrafo.
- **Idioma:** mensagens, `@Schema` e `@DisplayName` em português; identificadores de
  código, campos JSON e `code` de erro em inglês.
- **Ausência é `Optional`** no acessor de leitura, nunca `null` devolvido ao chamador.
- **OpenAPI mora fora do controller**, numa interface `*Swagger` que ele implementa.

---

## 6. Manual — criando um módulo novo

Do zero, na ordem em que as coisas devem ser escritas. Cada passo tem seu teste
antes do código; o [documento de testes](testes.md) traz as receitas.

Vamos usar `survey` como exemplo.

### 6.1. O domínio

**Identificador** — `modules/survey/domain/entities/SurveyId.java`:

```java
public final class SurveyId extends Id {
  private SurveyId(String value) { super(value); }

  public static SurveyId generate() { return new SurveyId(newValue()); }
  public static SurveyId of(String value) { return new SurveyId(value); }
}
```

**Value objects** — `modules/survey/domain/valueobjects/Title.java`. Um `record`
que se valida no compact constructor:

```java
public record Title(String value) {

  private static final int MAX_LENGTH = 200;
  private static final String INVALID_CODE = "survey.title_invalid";

  public Title {
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Título é obrigatório");
    }
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE,
          "Título não pode passar de " + MAX_LENGTH + " caracteres");
    }
  }

  public static Title of(String value) { return new Title(value); }

  @Override
  public String toString() { return value; }
}
```

Note o padrão: código do erro em constante privada, `ErrorType.VALIDATION` para
formato, factory `of(...)` nomeada, `toString()` devolvendo o valor cru.

**Entidade** — `modules/survey/domain/entities/Survey.java`:

```java
@Getter
public final class Survey extends Entity<SurveyId> {

  private final Instant createdAt;
  private Title title;
  private Instant updatedAt;

  private Survey(SurveyId id, Title title, Instant createdAt, Instant updatedAt) {
    super(id);
    this.title = title;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Survey que nasce agora. */
  public static Survey create(Title title) {
    var now = Instant.now();
    return new Survey(SurveyId.generate(), title, now, now);
  }

  /** Survey que volta do banco: identidade e carimbos já existem. */
  public static Survey restore(SurveyId id, Title title, Instant createdAt, Instant updatedAt) {
    return new Survey(id, title, createdAt, updatedAt);
  }

  public void rename(Title title) {
    this.title = Objects.requireNonNull(title, "title é obrigatório");
    touch();
  }

  private void touch() { this.updatedAt = Instant.now(); }
}
```

Sempre: construtor privado, `create` e `restore` como as duas portas de entrada,
mutação por método de negócio (`rename`, `activate`) que chama `touch()` no fim, e
validação de campo em setter privado quando houver regra.

### 6.2. A porta de repositório

`modules/survey/application/repositories/SurveyRepository.java`. Interface pura,
falando a linguagem do domínio:

```java
public interface SurveyRepository {
  Survey create(Survey survey);
  Survey update(Survey survey);
  Optional<Survey> findById(SurveyId id);
}
```

Só os métodos que algum caso de uso realmente chama. Nada de CRUD por reflexo.

### 6.3. O caso de uso

`modules/survey/application/usecases/CreateSurveyUseCase.java`:

```java
@Slf4j
public class CreateSurveyUseCase
    implements UseCase<CreateSurveyUseCase.Input, CreateSurveyUseCase.Output> {

  private final SurveyRepository surveys;

  public CreateSurveyUseCase(SurveyRepository surveys) {
    this.surveys = surveys;
  }

  @Override
  public Output execute(Input input) {
    var survey = Survey.create(Title.of(input.title()));

    surveys.create(survey);

    log.info("Pesquisa criada [{}]", survey.id().value());

    return new Output(survey.id().value());
  }

  public record Input(String title) {}
  public record Output(String id) {}
}
```

Sem anotação de framework. Construtor recebe as portas. `Input`/`Output` aninhados.

Erro recorrente vira classe em `application/errors/`, estendendo a família certa.

### 6.4. A borda HTTP

**DTO de entrada** — `record` com Bean Validation. A mensagem de cada constraint
**espelha a do value object**: o cliente recebe o mesmo texto tendo o erro parado em
`@Size` ou em `Title`.

```java
@Schema(description = "Dados para criar uma pesquisa")
public record CreateSurveyRequestDTO(
    @NotBlank(message = "Título é obrigatório")
        @Size(max = 200, message = "Título não pode passar de 200 caracteres")
        @Schema(description = "Título da pesquisa", example = "NPS trimestral",
                maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
        String title) {

  public CreateSurveyUseCase.Input toInput() {
    return new CreateSurveyUseCase.Input(title);
  }
}
```

O caso de uso **nunca** recebe o DTO — recebe o `Input`.

**Presenter** — `final class` em `modules/survey/infra/http/presenters/`, com
construtor privado e um método estático:

```java
public final class CreateSurveyPresenter {

  private CreateSurveyPresenter() {}

  public static CreateSurveyResponseDTO present(CreateSurveyUseCase.Output output) {
    return new CreateSurveyResponseDTO(output.id());
  }
}
```

O controller chama `CreateSurveyPresenter.present(output)` — sem injetar nada. O DTO de
resposta é `record` puro, sem factory estática: o presenter é o único que conhece os
dois lados.

Listagem devolve `PageResponseDTO<T>` (`infra/http/dtos`), o envelope compartilhado, e o
presenter traduz cada item.

**Interface de documentação** — `SurveyControllerSwagger`, com `@Tag`,
`@Operation` e um `@ApiResponse` para **cada** status que o endpoint pode devolver,
inclusive 400, 409 e 422, apontando para `ApiErrorResponse` /
`ApiValidationErrorResponse` com media type `application/problem+json`.

**Controller** — implementa a interface, sem lógica:

```java
@RestController
@RequestMapping("/surveys")
public class SurveyController implements SurveyControllerSwagger {

  private final CreateSurveyUseCase createSurvey;
  private final CreateSurveyHttpPresenter presenter;

  SurveyController(CreateSurveyUseCase createSurvey, CreateSurveyHttpPresenter presenter) {
    this.createSurvey = createSurvey;
    this.presenter = presenter;
  }

  @Override
  @PostMapping
  public ResponseEntity<CreateSurveyResponseDTO> create(
      @Valid @RequestBody CreateSurveyRequestDTO request) {
    var output = createSurvey.execute(request.toInput());
    var location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}").buildAndExpand(output.id()).toUri();
    return ResponseEntity.created(location).body(presenter.present(output));
  }
}
```

Criação bem-sucedida responde **201 com header `Location`**.

### 6.5. A migration

`src/main/resources/db/migration/V20260908143000__create_surveys.sql`. Timestamp em
UTC no nome. Unicidade de negócio é constraint no banco, não só checagem no caso de
uso. **Todo campo usado em filtro, ordenação ou junção ganha índice na mesma
migration que introduz a consulta.**

### 6.6. A persistência

Entidade JPA (`@Entity`, `@Table`, Lombok `@Getter @Setter
@NoArgsConstructor(access = PROTECTED) @AllArgsConstructor`), mapper estático,
interface Spring Data e o adaptador que implementa a porta.

```java
public interface SurveyJpaRepository extends JpaRepository<SurveyJpaEntity, String> {
  Optional<SurveyJpaEntity> findByTitle(String title);
}

@Repository
public class SurveyRepositoryJpa implements SurveyRepository {
  private final SurveyJpaRepository repository;
  // ... delega, mapeando nas duas pontas
}
```

`EntityManager` direto é proibido enquanto `save()` resolver.

### 6.7. O cabeamento

`modules/survey/infra/config/UseCasesConfiguration.java`:

```java
@Configuration(proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  CreateSurveyUseCase createSurveyUseCase(SurveyRepository repository) {
    return new CreateSurveyUseCase(repository);
  }
}
```

Um arquivo por módulo. É o preço de manter o caso de uso livre de anotação — e é
onde fica visível, em um lugar só, tudo de que cada caso de uso depende.

---

## 7. Manual — casos menores

### Adicionar um caso de uso a um módulo que já existe

1. O método novo na porta de repositório, se precisar de um.
2. O método no fake in-memory de `testsupport/repositories`.
3. Teste do caso de uso, com o fake.
4. O caso de uso.
5. Implementação do método no adaptador JPA.
6. DTO, presenter, `@ApiResponse` na interface Swagger, método no controller.
7. Teste E2E.
8. `@Bean` no `UseCasesConfiguration` do módulo.

### Adicionar um erro novo

Existe um `ErrorType` que descreve a natureza? Então é só uma classe nova em
`application/errors/`, estendendo a família correspondente, com o `code` em
constante e o dado relevante como campo.

Se **nenhum** `ErrorType` serve, aí sim: acrescente o valor ao enum e o mapeamento
em `ErrorTypeHttpStatus`. É uma decisão de arquitetura — o `switch` de nível de log
em `ApiExceptionHandler` também precisa cobri-lo (ele é exaustivo, o compilador
avisa), e vale um ADR.

### Precisar de transação

Só quando o caso de uso escreve em mais de um agregado:

```java
public CreateXUseCase(XRepository x, YRepository y, Transactor transactor) { ... }

@Override
public Output execute(Input input) {
  return transactor.inTransaction(() -> {
    // as duas escritas
  });
}
```

Nos testes, use `DirectTransactor` de `testsupport/transaction`, que executa
direto e conta as invocações.

---

## 8. Checklist de review

- [ ] Nenhum `import org.springframework` / `jakarta.persistence` / `io.swagger` em
      `core` ou em `modules/*/domain`
- [ ] Caso de uso sem anotação de framework, cabeado por `@Configuration`
- [ ] Invariante validada no construtor do domínio, não no caso de uso
- [ ] `code` de erro em constante, no formato `<contexto>.<motivo>`
- [ ] Nenhum `try/catch` traduzindo erro para HTTP
- [ ] Rota sem `/api`
- [ ] `@ApiResponse` para todo status possível, com o schema de erro certo
- [ ] Mensagem do DTO espelha a mensagem do value object
- [ ] Migration nova para toda mudança de schema; nenhuma migration existente editada
- [ ] Índice para todo campo consultado; listagem paginada; sem N+1
- [ ] Nada de entrada do usuário no log
- [ ] `./mvnw verify` verde

---

## 9. O que ainda não existe

Registrado para que ninguém procure e conclua que sumiu:

| Peça | Situação | Quando entra |
|---|---|---|
| `Presenter` | padrão adotado, sem implementação | no próximo endpoint escrito |
| `AggregateRoot` | não existe | quando houver evento de domínio a acumular |
| Uso do `Transactor` | porta e adaptador prontos, sem consumidor | primeiro caso de uso com duas escritas |
| Autenticação | não existe | `UNAUTHORIZED`/`FORBIDDEN` já estão previstos no core |
| Cache | não existe | quando houver problema medido a resolver |
| SLO numérico | não definido | quando houver baseline de tráfego real |

Abstração entra quando existe o segundo caso concreto que a exige, nunca por
antecipação.
