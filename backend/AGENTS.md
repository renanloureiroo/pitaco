# AGENTS.md — backend do Pitaco

API do Pitaco: Java 21 + Spring Boot 4.1, PostgreSQL + Flyway, Redis, OpenTelemetry
(stack Grafana LGTM), JUnit 5 + Testcontainers, Maven com wrapper.

Este arquivo é o mínimo que vale em toda tarefa. O detalhe está nos documentos
listados no fim — e nas skills `pitaco-backend` (escrever código) e `pitaco-tests`
(escrever teste), que trazem o roteiro passo a passo.

---

## A regra da qual todas as outras derivam

A dependência aponta em uma única direção: **`infra` conhece `core`; `core` não
conhece ninguém.**

Abra qualquer arquivo em `core/` ou em `modules/*/domain/` e procure por
`import org.springframework`, `jakarta.persistence`, `jakarta.validation` ou
`io.swagger`. Se achar, está errado. Lombok é permitido só no que não vaza
framework (`@Getter`, `@Slf4j`).

```
com.renanloureiroo.pitaco
├── core        entity/ error/ identity/ pagination/                 Java puro
│            transaction/ usecase/
├── infra       PitacoApplication, http/config, http/error, transaction/
└── modules/<contexto>
    ├── domain        entidades, value objects, enums de estado    Java puro
    ├── application   casos de uso, portas (repositories/), errors/  Java puro
    └── infra         controllers, dtos, database/jpa, config/     Spring aqui
```

`core` é para o que **dois ou mais** módulos usam. Na dúvida, nasce no módulo.

---

## Invioláveis

**Domínio**

- Invariante é validada no construtor (compact constructor do `record`, construtor
  privado da entidade) e lança `DomainException` de dentro do domínio — nunca do
  caso de uso.
- Entidade = identidade → estende `Entity<ID extends Id>`. Value object = conteúdo
  → é `record`. Todo identificador estende `Id`, com `generate()` e `of()`.
- Entidade tem `create(...)` (nasce agora) e `restore(...)` (volta do banco).
  Mapper chama `restore`, nunca `create`.
- Ausência é `Optional` no acessor de leitura, nunca `null` devolvido.

**Erro**

- Todo erro estende `ApplicationException` e carrega `ErrorType` + `code` textual
  estável no formato `<contexto>.<motivo>`, em constante `private static final`.
- `code` é contrato público; a **mensagem é livre**. Nunca escreva teste que afirma
  sobre mensagem (exceto teste de DTO e E2E, onde o contrato é justamente esse).
- Erro recorrente vira classe nomeada em `application/errors/`, carregando o dado
  relevante — não só a mensagem.
- Status HTTP sai de `ErrorTypeHttpStatus`. Precisa de status novo? Crie um
  `ErrorType`, não um atalho na borda.

**Caso de uso**

- Implementa uma das quatro interfaces de `core.usecase`. Um `execute`.
- `Input`/`Output` são `record` aninhados na própria classe.
- **Sem anotação de framework.** Nada de `@Service`. É cabeado por `@Bean` no
  `UseCasesConfiguration` do módulo.
- Recebe `Input`, nunca o DTO.

**Borda HTTP**

- Resposta é montada por presenter em `modules/<contexto>/infra/http/presenters`:
  `final class` com construtor privado e um `public static R present(O output)` — mesmo
  molde do mapper JPA, porque é a mesma coisa, tradução pura sem estado. Não existe
  interface `Presenter` nem bean; o controller chama `XPresenter.present(output)`. DTO de
  resposta não tem factory estática e não conhece o caso de uso.
- Rota **não** repete `/api` — vem do `context-path`. Use `@RequestMapping("/x")`.
- `try/catch` traduzindo erro para HTTP é proibido. `ApiExceptionHandler` é o ponto
  único; toda resposta de erro é RFC 9457 com `code` e `traceId`.
- OpenAPI mora numa interface `*Swagger` que o controller implementa, com um
  `@ApiResponse` para **cada** status possível (400, 409, 422 inclusive).
- A mensagem de cada constraint do DTO **espelha** a do value object correspondente.
- Criação responde 201 com header `Location`.

**Persistência**

- Repositório é porta em `application/repositories/`, implementada em `infra` sobre
  Spring Data, com mapper explícito domínio↔JPA. `EntityManager` direto é proibido.
- Transação é decisão do **caso de uso**, declarada com `@Transactional` de
  `core.transaction` **no `execute`** — a anotação do projeto, não a do Spring. Quem a implementa é o
  `TransactionalAdvisor`, na infra, sobre a porta `Transactor`. Nunca `@Transactional`
  escondido num repositório Spring Data. Entra quando há duas escritas **ou** quando uma
  leitura e uma escrita precisam do mesmo instante do banco (é o caso do `update`
  condicional do `RevokeApiKeyUseCase`). Um `save` sozinho não precisa.
- Entidade JPA é separada da entidade de domínio. Sempre.
- Migration em `src/main/resources/db/migration`, padrão
  `V<yyyyMMddHHmmss>__descricao.sql` com timestamp **UTC**. `ddl-auto` é `validate`.
  **Migration aplicada nunca se edita** — correção é migration nova.
- Todo campo usado em filtro, ordenação ou junção ganha índice na mesma migration.
  Listagem é paginada. Sem N+1. Sem chamada a repositório dentro de laço.
- Consulta paginada usa `core.pagination`: a porta devolve `Page<T>` (itens + total) e a
  `Query` aninhada implementa `PageQuery` (`page`/`size`/`offset`), acrescentando os
  próprios filtros. Na borda, o envelope é `PageResponseDTO<T>` de `infra/http/dtos`.
  `Pageable`/`Page` do Spring Data não passam de `infra`.

**Teste**

- Mock sobre porta do projeto é **proibido** — use o fake de `testsupport/`
  (`InMemoryApplicationRepository`, `InMemoryApiKeyRepository`, `DirectTransactor`).
  Mockar biblioteca de
  terceiro (ex.: `Tracer`) é aceitável.
- Dado vem de factory fluente (`ApplicationFactory.anApplication()`).
- Asserção é AssertJ. Nada de `assertTrue(a.equals(b))`.
- E2E usa `@E2E` + `DatabaseCleaner` no `@BeforeEach`. **Nunca `@Transactional`** —
  o servidor atende em outra thread e a transação do teste não vê o commit.
- Endpoint só está pronto com E2E cobrindo: caminho feliz (status, corpo, `Location`
  **e o estado lido de volta do banco**), 400 de validação, cada erro do OpenAPI,
  JSON malformado, e que o estado não mudou nos caminhos de falha.

**Geral**

- Log sempre via `@Slf4j`. `LoggerFactory` explícito é proibido. Erro é logado uma
  vez só, na borda. **Entrada do usuário nunca vai para o log** — campo e motivo, não
  o valor rejeitado.
- Tempo é `Instant` em UTC.
- **Sem Javadoc e sem comentário explicativo de forma desnecessária.** O código se explica sozinho; comentário
  de "o quê" é ruído. A exceção é a **decisão** com alternativa descartada — uma linha,
  não um parágrafo. Nunca documente a assinatura. Regra não óbvia vira teste.
- Idioma: mensagens, `@Schema` e `@DisplayName` em **português**; identificadores,
  campos JSON e `code` de erro em **inglês**.
- Formatação é google-java-format, aplicada pelo editor. Não adicione plugin de
  formatação ao build Maven.
- Abstração entra quando existe o **segundo** caso concreto que a exige.

---

## Ordem de trabalho

1. Value objects e entidade — com os testes de invariante antes
2. Porta de repositório + fake em `testsupport`
3. Caso de uso — com teste sobre o fake antes
4. Migration + entidade JPA + mapper + adaptador
5. DTO de entrada — com teste de constraints antes
6. Presenter, interface Swagger, controller
7. Teste E2E
8. `@Bean` no `UseCasesConfiguration` do módulo

Correção de bug entra com o teste que o reproduz, escrito **antes** da correção.

---

## Comandos

```bash
./mvnw test                     # suíte completa (256 testes; Docker precisa estar de pé)
./mvnw verify                   # testes + empacotamento — o portão antes de qualquer PR
./mvnw test -Dtest=SlugTest     # uma classe
./mvnw spring-boot:run          # sobe a app; Postgres/Redis/LGTM sobem junto
```

Sem Docker, é preciso excluir as quatro classes que sobem contexto:
`-Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'`.

API em `http://localhost:8080/api` · Swagger em `/api/swagger-ui.html`.

---

## O que ainda não existe

Não procure — não sumiu, ainda não nasceu: `AggregateRoot` (só com evento de domínio),
autenticação, cache Redis, SLO numérico.

---

## Onde está o resto

| Arquivo                                                              | O que traz                                                          |
| -------------------------------------------------------------------- | ------------------------------------------------------------------- |
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | os princípios obrigatórios e os portões de qualidade                |
| [`docs/backend/arquitetura.md`](docs/backend/arquitetura.md)         | as camadas em detalhe e o manual de criação, com exemplos completos |
| [`docs/backend/testes.md`](docs/backend/testes.md)                   | os seis tipos de teste e a receita de cada um                       |
| [`docs/adrs`](docs/adrs)                                             | as decisões tomadas e o que se aceitou pagar por elas               |
| [`README.md`](README.md)                                             | como rodar, endereços, formato de erro                              |

Skills: **`pitaco-backend`** para escrever código novo, **`pitaco-tests`** para
escrever teste.

Se algum destes divergir da constituição, a constituição vence e o outro está
desatualizado.
