<!--
Sync Impact Report
==================
Version change: 1.1.0 → 1.2.0
Bump rationale: MINOR — o Redis saiu da stack fixa. Não é PATCH porque muda o significado do
texto (uma dependência deixa de fazer parte do padrão obrigatório, e o Testcontainers do E2E
deixa de subi-la), e não é MAJOR porque nenhum princípio foi removido nem invertido: o
Princípio V continua dizendo que cache só entra com problema medido — o que mudou é que agora
não há nem a dependência ociosa esperando por ele. Motivo: o Redis atravessou as features 001,
002, 003 e o planejamento da 004 sem uma linha de código que o usasse, custando um container
em toda execução de E2E e duas dependências no classpath. Impacto no código existente: nenhum
comportamento muda — só o bean `redisContainer` de `TestcontainersConfiguration`, as duas
dependências do `pom.xml`, o serviço do `compose.yaml` e o bloco de
`application-local.yml.example` foram removidos na mesma alteração.

Version change anterior: 1.0.1 → 1.1.0
Bump rationale: MINOR — o presenter deixou de ser porta em `core` e passou a ser
`final class` com método estático em `modules/<módulo>/infra/http/presenters`, no mesmo
molde dos mappers JPA. `core/presenter/Presenter.java` foi removido. Não é MAJOR porque
nenhum princípio foi removido ou invertido: o Princípio IV segue exigindo que a saída
seja montada por um presenter e nunca pelo controller — mudou o mecanismo, não a regra —,
e os quatro presenters e dois controllers migraram na mesma alteração, sem deixar código
inválido pendente. Motivo da mudança: `Presenter` era chamado de porta sem ser uma; quem
o declarava, implementava e consumia era `infra`, então nenhuma dependência era invertida
por ele. Sendo função pura de tradução, alinhou-se a `ApiKeyJpaMapper`/`ApplicationJpaMapper`.

Version change anterior: 1.0.0 → 1.0.1
Bump rationale: PATCH — o presenter deixou de ser pendência. A feature
001-api-key-management introduziu `core/presenter/Presenter.java`, os presenters de
`modules/app/infra/http/presenters` e migrou `CreateApplicationResponseDTO.from(...)`.
Sem mudança de regra: o texto passa a descrever o que já existe no repositório.

Version change anterior: (template, não versionado) → 1.0.0
Bump rationale: MAJOR inicial — primeira ratificação da constituição com conteúdo
concreto substituindo o scaffold de placeholders.

Modified principles:
- [PRINCIPLE_1_NAME] → I. Núcleo Independente de Framework
- [PRINCIPLE_2_NAME] → II. Domínio Que Se Valida
- [PRINCIPLE_3_NAME] → III. Teste É Contrato (NÃO-NEGOCIÁVEL)
- [PRINCIPLE_4_NAME] → IV. Consistência da Superfície Pública
- [PRINCIPLE_5_NAME] → V. Performance Por Construção

Added sections:
- Padrões Técnicos Adicionais (era [SECTION_2_NAME])
- Fluxo de Desenvolvimento e Portões de Qualidade (era [SECTION_3_NAME])

Removed sections: nenhuma.

Follow-up TODOs:
- Metas numéricas de latência (SLO) deliberadamente ausentes: entram por emenda
  quando houver baseline de tráfego real.
-->

# Pitaco Constitution

Esta constituição descreve o padrão já escrito no código do backend do Pitaco e o
torna obrigatório. Ela não é aspiracional: cada regra abaixo tem exemplo vivo no repositório.

## Core Principles

### I. Núcleo Independente de Framework

A dependência aponta em uma única direção: `infra` conhece `core` e `modules/*/domain`;
o núcleo não conhece ninguém.

- `core` e `modules/*/domain` **NÃO PODEM** importar `org.springframework.*`,
  `jakarta.persistence.*`, `jakarta.validation.*`, `io.swagger.*` nem qualquer tipo de
  protocolo (`HttpStatus`, `ResponseEntity`). Lombok é permitido apenas para o que não
  vaza semântica de framework (`@Getter`, `@Slf4j`).
- Cada módulo segue as camadas `domain` → `application` → `infra`. `application` define
  as portas de saída do caso de uso (`ApplicationRepository`, `ApiKeyRepository`); `infra`
  fornece os adaptadores. Um caso de uso **NUNCA** referencia um tipo de `infra`.
- `core` guarda só o que **dois ou mais** módulos compartilham e o que inverte dependência
  de fato. Tradução pura entre camadas — presenter e mapper JPA — não inverte nada: é
  declarada, implementada e consumida dentro de `infra`, e por isso não mora em `core`.
- Casos de uso são POJOs sem anotação de framework, instanciados por `@Configuration`
  explícita (`UseCasesConfiguration`). `@Service` em caso de uso é proibido — o
  cabeamento é decisão da infraestrutura, não do caso de uso.
- Todo caso de uso implementa uma das quatro interfaces funcionais de `core.usecase`
  (`UseCase`, `UseCaseWithoutInput`, `UseCaseWithoutOutput`, `UseCaseWithoutInputAndOutput`).
  Um caso de uso, um `execute`, com `Input`/`Output` declarados como `record` aninhados
  na própria classe.
- Persistência é acessada via porta de repositório do módulo, implementada sobre Spring
  Data (`ApplicationJpaRepository`) com um mapper explícito domínio↔JPA. `EntityManager`
  direto é proibido enquanto `save()` resolver.

**Rationale**: trocar protocolo, ORM ou o próprio Spring não pode custar uma reescrita
de regra de negócio. A regra de importação é o que torna essa independência verificável
em vez de retórica.

### II. Domínio Que Se Valida

Um tipo de domínio em mãos é sempre um tipo válido: quem o recebe não checa de novo.

- Invariante é validada na construção — no compact constructor do `record` (`Slug`, `Name`)
  ou no construtor privado da entidade (`Application`) — e a violação lança
  `DomainException` de dentro do domínio, nunca do caso de uso.
- Entidade é identidade: estende `Entity<ID extends Id>` e iguala por tipo concreto +
  identificador. Value object é conteúdo: é `record`, e a igualdade estrutural já vem
  pronta. Não existe herança de value object.
- Todo identificador de agregado estende `Id`, é opaco (a geração é detalhe interno) e
  tipado — um `ApplicationId` nunca é igual a um `UserId`, nem em compilação nem em runtime.
- Toda exceção do sistema estende `ApplicationException` e carrega dois dados obrigatórios:
  um `ErrorType` (natureza, independente de protocolo) e um `code` textual estável no
  formato `<contexto>.<motivo>` (`application.slug_invalid`). O `code` é contrato público;
  a mensagem pode mudar a qualquer momento. Códigos são constantes `private static final`
  no tipo que os lança.
- Erro recorrente de um agregado vira classe nomeada na camada `application/errors`
  (`ApplicationAlreadyExistsWithSameSlug`), carregando o dado que a borda precisa devolver.
- Ausência é `Optional` no acessor de leitura (`quietPeriodDays()`), nunca `null` devolvido
  ao chamador.
- Javadoc documenta a decisão e o porquê, não a assinatura. Comentário que só repete o nome
  do método **NÃO PODE** existir. Regra não óbvia vira teste, não parágrafo.

**Rationale**: validação espalhada pelas bordas se perde na primeira borda nova. Concentrar
a invariante no construtor faz o compilador e o próprio tipo carregarem a garantia.

### III. Teste É Contrato (NÃO-NEGOCIÁVEL)

Toda regra de negócio nasce com teste que falha antes de existir código que a satisfaça.

- Três níveis, cada um com sua ferramenta:
  1. **Domínio** — JUnit puro, sem contexto Spring. Cobre cada invariante e cada caminho
     de rejeição, com `@ParameterizedTest` para as famílias de entrada válida/inválida.
  2. **Caso de uso** — JUnit puro sobre fakes in-memory de `testsupport/repositories`.
     Mock de framework (`@MockBean`, Mockito sobre a porta) é proibido: a porta tem
     implementação de teste real, que também exercita o contrato do repositório.
  3. **E2E** — anotação `@E2E`, servidor em porta aleatória, Testcontainers para Postgres
     e stack LGTM. Nenhum serviço externo. A limpeza é explícita via `DatabaseCleaner`;
     `@Transactional` em teste E2E é proibido, pois esconde o commit real.
- Um endpoint só é considerado pronto com E2E cobrindo, no mínimo: o caminho feliz
  (status, corpo, header `Location` quando aplicável **e** o estado persistido), a rejeição
  de validação e cada erro de negócio anunciado no OpenAPI.
- Dado de teste vem de factory fluente em `testsupport/factories` (`ApplicationFactory.anApplication()`),
  nunca de construção manual repetida entre testes.
- Asserção é AssertJ (`assertThat`, `assertThatThrownBy`). Ao afirmar um erro, o teste verifica
  `type` e `code` — não a mensagem, que é livre para mudar.
- Todo teste declara intenção legível: `@DisplayName` na classe descrevendo o alvo
  (`"POST /applications"`) e no método descrevendo o comportamento esperado.
- Correção de bug entra com o teste que o reproduz, escrito antes da correção.

**Rationale**: o fake in-memory e o Testcontainers custam mais para escrever que um mock,
e pagam esse custo já na primeira mudança de schema — o mock passaria verde enquanto o
sistema quebra.

### IV. Consistência da Superfície Pública

Dois endpoints escritos com seis meses de diferença precisam ser indistinguíveis para
quem consome a API.

- **Erro**: toda resposta de erro sai em RFC 9457 (`application/problem+json`), gerada
  exclusivamente pelo `ApiExceptionHandler`. `try/catch` traduzindo erro em controller é
  proibido. O corpo sempre traz `code` e, quando o tracing está ligado, `traceId`.
  Erro inesperado vira `internal.unexpected` genérico — mensagem interna **NUNCA** vaza
  ao cliente.
- **Status HTTP** é decidido em um único lugar, `ErrorTypeHttpStatus`, a partir do
  `ErrorType`. Um caso de uso que quer um status novo cria um `ErrorType`, não um atalho
  na borda.
- **Rota** nunca repete o prefixo `/api`, que vem do `context-path` do contêiner.
- **Entrada** é sempre um `record` DTO com Bean Validation, e a mensagem de cada
  constraint espelha a mensagem do value object correspondente — o cliente recebe o mesmo
  texto tendo o erro parado em `@Pattern` ou em `Slug`. O DTO converte para o input do caso
  de uso via `toInput()`; o caso de uso **NÃO PODE** receber o DTO.
- **Saída** é montada por um presenter, não pelo controller. O presenter é `final class`
  com construtor privado e um `public static R present(O output)`, em
  `modules/<módulo>/infra/http/presenters`, e é o único a conhecer o DTO de resposta —
  mesmo molde de `ApiKeyJpaMapper`, porque é a mesma natureza: função pura de tradução,
  sem estado nem dependência. O controller chama o caso de uso, passa o `Output` ao
  presenter e devolve. Montar o DTO dentro do controller é proibido. Presenter que um dia
  precise de dependência real vira `@Component` injetado — quando o caso concreto existir,
  não por antecipação.
- **Documentação** é obrigatória e mora fora do controller: uma interface `*Swagger`
  carrega `@Operation`, `@ApiResponses` e `@Schema`; o controller a implementa e fica
  com a lógica de rota apenas. Todo status que o endpoint pode devolver — inclusive 400,
  409 e 422 — é declarado com o schema de erro correspondente.
- **Idioma**: mensagens de erro, `@Schema` e `@DisplayName` em português; identificadores
  de código, nomes de campo JSON e `code` de erro em inglês.
- Criação bem-sucedida responde `201` com header `Location` apontando para o recurso.

**Rationale**: consistência de API é experiência do usuário para um backend. `code` estável,
formato de erro único e OpenAPI completo são o que permite um cliente tratar erro sem
fazer parsing de texto.

### V. Performance Por Construção

Não há SLO numérico enquanto não há tráfego real medido. Há, desde já, as regras
estruturais que um review consegue verificar.

- **Sem N+1**: toda travessia de associação em consulta que devolve coleção usa `join fetch`
  ou projeção explícita. Chamada a repositório dentro de laço é proibida.
- **Listagem é paginada**. Um endpoint de coleção **NUNCA** devolve resultado ilimitado;
  todo `findAll` sem filtro é uso interno ou de teste, nunca superfície pública.
- **Todo campo usado em filtro, ordenação ou junção tem índice**, criado na mesma migration
  que introduz a consulta. Unicidade de negócio (o `slug` de uma aplicação) é garantida por
  constraint no banco, não apenas pela checagem do caso de uso.
- **Sem I/O bloqueante em laço**: operação em lote usa `saveAll`/consulta única, não uma
  chamada por item.
- Virtual threads estão ligadas: código de request **NÃO PODE** usar `synchronized` em torno
  de I/O nem depender de `ThreadLocal` para estado de requisição.
- Mudança de comportamento sob carga (nova consulta em caminho quente, novo índice, novo
  cache) é justificada no PR com o motivo; cache só entra quando há um problema medido
  para resolver.

**Rationale**: as regras acima previnem as falhas de performance que não aparecem em
desenvolvimento e só surgem com volume — quando o custo de corrigir já inclui migração
de dados.

## Padrões Técnicos Adicionais

**Stack fixa**: Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator),
PostgreSQL + Flyway, OpenTelemetry sobre stack Grafana LGTM, springdoc-openapi,
JUnit 5 + AssertJ + Testcontainers, Maven com wrapper. Dependência nova exige justificativa
no PR — e dependência que atravessa uma feature inteira sem uso sai, como o Redis saiu.

**Migrations**: vivem em `src/main/resources/db/migration` no padrão
`V<yyyyMMddHHmmss>__descricao.sql`, com timestamp em UTC — duas branches nunca disputam a
mesma versão. `ddl-auto` é `validate` e permanece assim: o schema nasce da migration, nunca
do Hibernate. Migration aplicada **NÃO PODE** ser editada; correção é migration nova.

**Tempo**: todo instante é `Instant`, gravado e lido em UTC. A JVM roda com
`-Duser.timezone=UTC` em execução e em teste. Fuso é assunto da apresentação, nunca do
armazenamento.

**Log**: sempre via `@Slf4j` do Lombok — `LoggerFactory` explícito é proibido. Erro é logado
uma única vez, na borda HTTP, com nível derivado do `ErrorType`
(`NOT_FOUND`/`VALIDATION` → DEBUG, `CONFLICT`/`BUSINESS_RULE` → INFO,
`UNAUTHORIZED`/`FORBIDDEN` → WARN, inesperado → ERROR). Caso de uso loga sucesso, não erro.
Entrada do usuário **NUNCA** vai para o log: registra-se o campo e o motivo, jamais o valor
rejeitado.

**Transação**: a porta `Transactor` existe e a implementação Spring está pronta. Um caso de
uso só a utiliza quando realmente escreve em mais de um agregado — escrita única fica sem
transação explícita.

**Formatação**: google-java-format, aplicado pela extensão do editor. Não há plugin de
formatação no build Maven, e não deve haver.

**Configuração**: `application.yml` documenta em comentário o porquê de cada ajuste não
óbvio. Segredo **NUNCA** é versionado; `application-local.yml` está no `.gitignore` e apenas
o `.example` entra no repositório.

## Fluxo de Desenvolvimento e Portões de Qualidade

**Ordem de trabalho** para uma funcionalidade nova:
1. Domínio (entidade, value objects, erros nomeados) com os testes de invariante.
2. Porta de repositório na camada `application` e fake in-memory em `testsupport`.
3. Caso de uso com testes sobre o fake.
4. Migration Flyway e adaptador JPA (entidade, mapper, repositório).
5. Borda HTTP: DTO de entrada, presenter, interface `*Swagger`, controller.
6. Teste E2E cobrindo caminho feliz e cada erro anunciado.

**Portões obrigatórios** — nenhum merge passa sem todos:
- `./mvnw verify` verde, sem teste ignorado ou desabilitado sem justificativa escrita.
- Nenhuma importação de framework em `core` ou em `modules/*/domain`.
- Todo endpoint novo ou alterado com OpenAPI completo e E2E correspondente.
- Toda mudança de schema com migration; nenhuma migration existente editada.
- README atualizado quando uma decisão arquitetural muda.

**Review** verifica explicitamente a conformidade com os cinco princípios. Uma violação
consciente é aceitável apenas se justificada no PR com o motivo e o custo da alternativa —
justificativa ausente é motivo suficiente para bloquear o merge.

**Complexidade** precisa se pagar. Abstração nova (`AggregateRoot`, camada de cache, evento
de domínio) entra quando existe o segundo caso concreto que a exige, nunca por antecipação.

## Governance

Esta constituição prevalece sobre qualquer outra prática do projeto. Onde ela conflitar com
um hábito, um exemplo antigo ou uma sugestão de ferramenta, ela vence.

**Emenda** é feita por PR que altera este arquivo, descrevendo a mudança, o motivo e o
impacto no código existente. Emenda que invalida código já escrito acompanha o plano de
migração — data-limite ou tarefa registrada.

**Versionamento** semântico:
- **MAJOR** — princípio removido ou redefinido de forma incompatível com o código existente.
- **MINOR** — princípio ou seção nova, ou orientação materialmente ampliada.
- **PATCH** — esclarecimento, correção de texto, refinamento sem mudança de significado.

**Conformidade** é verificada em todo review. Os agentes e comandos de Spec Kit
(`/speckit-plan`, `/speckit-tasks`, `/speckit-implement`, `/speckit-analyze`) leem este
arquivo em tempo de execução e devem recusar planos que o violem. Para orientação de
desenvolvimento no dia a dia, o `README.md` é o complemento operacional desta constituição.

**Version**: 1.2.0 | **Ratified**: 2026-09-05 | **Last Amended**: 2026-09-08
