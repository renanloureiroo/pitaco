# Implementation Plan: Autoria de pesquisa — rascunho, perguntas, disparo, regras e versões

**Branch**: `003-survey-authoring` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-survey-authoring/spec.md`

## Summary

O Pitaco passa a saber o que existe para saber: a pesquisa. Um módulo novo, `modules/survey`,
com o caminho inteiro da autoria — criar o rascunho, escrever as perguntas, configurar o disparo
e as regras de segmentação, publicar congelando o conteúdo em uma versão imutável, controlar o
que está no ar e abrir versões novas com classificação de mudança.

Três decisões carregam o plano.

A primeira resolve uma contradição da própria spec: as *Key Entities* dizem que a pergunta
pertence à versão e que o disparo pertence à pesquisa, mas FR-025 congela pergunta, disparo e
regra com o mesmo ato. Só há um jeito de congelar três coisas de uma vez — **`SurveyVersion` é o
recipiente de todo o conteúdo, e o rascunho é a versão em estado `DRAFT`** (D-04). "Um disparo por
pesquisa" continua verdadeiro para quem usa, e vira invariante verificável: um por versão.

A segunda separa duas coisas que o Princípio II poderia confundir. Um rascunho aceita pergunta de
escolha **sem** opção (US2, cenário 3), mas recusa pergunta de texto livre **com** opção. A linha
não é entre rigor e frouxidão, é entre o que está **errado** e o que está **faltando**:
**coerência é invariante de construção, completude é regra de publicação** (D-08). O rascunho
segue sendo a válvula de segurança que substitui o ambiente de homologação que não existe, e uma
`Question` em mãos segue sendo sempre coerente.

A terceira segue o precedente que 002 abriu com `ApiKeyStatus`: o **estado exposto é derivado**
do ciclo de vida armazenado somado à janela e ao instante da leitura (D-05). É a premissa
explícita da spec — agendada vira ativa sem rotina agendada — e resolve dois edge cases de graça:
pausada com janela fechada continua pausada, e publicar no instante exato da abertura dá ativa.

A chegada do segundo módulo cobra **uma** promoção que a constituição já tinha datado para o
"segundo caso concreto": `ApplicationId` sobe para `core/identity` (D-02), porque agora dois
módulos o compartilham. A outra que a versão anterior deste plano previa — `Page<T>` para
`core/pagination` — deixou de ser tarefa: a feature 002 já a entregou, junto do envelope
`PageResponseDTO<T>` em `infra/http/dtos`, e esta fatia apenas os consome (D-13).

O presenter também mudou de forma sob a constituição 1.1.0: deixou de ser porta em `core` e
passou a ser `final class` com `public static R present(O output)` em
`modules/<módulo>/infra/http/presenters`, no mesmo molde dos mappers JPA. Os 13 presenters desta
fatia nascem assim, e nenhum deles é candidato à exceção do `@Component` injetado (D-19).

## Technical Context

**Language/Version**: Java 21 (virtual threads ligadas, `-Duser.timezone=UTC`)

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), springdoc-openapi,
Lombok, Flyway. **Nenhuma dependência nova.**

**Storage**: PostgreSQL. Seis tabelas novas — `surveys`, `survey_versions`, `questions`,
`question_options`, `segmentation_rules`, `survey_state_transitions` — em uma migration única,
`V20260908180000__create_surveys.sql`. `ddl-auto` segue `validate`. Nenhuma tabela existente é
alterada. Redis não é tocado.

**Testing**: JUnit 5 + AssertJ nos três níveis — domínio (invariantes, derivação de estado,
impedimentos, classificação de mudança), caso de uso sobre fakes in-memory
(`InMemorySurveyRepository`, `InMemorySurveyVersionRepository`,
`InMemorySurveyStateTransitionRepository`, `InMemoryApplicationScope`, `DirectTransactor`), E2E
com `@E2E` + Testcontainers e `DatabaseCleaner`. Zero mock sobre porta do projeto.

**Target Platform**: servidor Linux, JVM 21.

**Project Type**: web service (API REST monolítica, modular por contexto).

**Performance Goals**: sem SLO numérico (a constituição os adia até haver baseline de tráfego).
As regras estruturais desta fatia: ler uma versão traz perguntas, opções e regras em uma consulta
com `join fetch` — nunca uma consulta por pergunta; a listagem de pesquisas é O(size) sobre o
índice `(application_id, created_at desc, id desc)`; a reordenação é um `saveAll`, não uma escrita
por pergunta.

**Constraints**: nenhuma escrita em caminho de leitura, inclusive na derivação de estado (D-05) e
na derivação das transições de janela (D-06). Versão publicada é imutável: nenhuma operação de
autoria a alcança (SC-003). Fora do escopo da aplicação dona é sempre 404, nunca 403 (SC-004,
D-16). Entrada do usuário não vai para o log — campo e motivo, nunca o valor recusado.

**Scale/Scope**: um módulo novo, seis tabelas, 23 endpoints, 23 casos de uso, 6 entidades de
domínio, 10 value objects, 6 enums, 4 portas, 14 erros nomeados, 13 presenters. Uma promoção para `core`
(`ApplicationId`); `core/pagination` e `PageResponseDTO<T>` vêm prontos de 002.
Fora de escopo, por premissa da spec: entrega, coleta, resultado,
cota, prioridade, isenção de intervalo de descanso, lógica condicional entre perguntas, preview,
catálogo de eventos e atributos observados, modelos prontos, duplicação de pesquisa, leitura
consolidada entre versões, autenticação e o runbook de uso do produto.

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como este plano atende | Situação |
| --- | --- | --- |
| **I. Núcleo independente de framework** | `modules/survey/domain` e `modules/survey/application` são Java puro: nenhum `org.springframework`, `jakarta.*` ou `io.swagger`. Cada caso de uso implementa uma das quatro interfaces de `core.usecase`, com `Input`/`Output` como `record` aninhados e **sem** `@Service` — cabeados por `@Bean` no `UseCasesConfiguration` do próprio módulo. A dependência do módulo `app` não entra pelo domínio: é a porta `ApplicationScope`, com o adaptador na infra (D-03). `core` recebe só `ApplicationId`, que dois módulos compartilham (D-02) — presenter e mapper ficam de fora dele por não inverterem dependência nenhuma, como a constituição 1.1.0 explicita. `Pageable`/`Page` do Spring Data existem apenas dentro dos adaptadores JPA; o tipo que atravessa a porta é o `core.pagination.Page<T>` que 002 já entregou, e as consultas do módulo implementam `core.pagination.PageQuery` (D-13). Persistência por Spring Data com mapper explícito domínio↔JPA, sem `EntityManager`. | ✅ |
| **II. Domínio que se valida** | Toda invariante mora no compact constructor do `record` ou no construtor privado da entidade, e lança `DomainException` de dentro do domínio. A fronteira entre invariante e regra de publicação é explícita e justificada (D-08): o rascunho incompleto não é uma exceção ao princípio, é a distinção entre errado e faltando. Cinco identificadores novos estendem `Id`, todos com `generate()`/`of()`, e `QuestionKey` é `record` porque marca linhagem, não identidade (D-07). Cada entidade tem `create` e `restore`; o mapper chama `restore`. Ausência é `Optional` — `publishedVersionNumber`, `trigger`, `range`, `changeKind`. Cada erro carrega `ErrorType` e `code` `<contexto>.<motivo>` em constante `private static final`. | ✅ |
| **III. Teste é contrato** | Cada artefato entra depois do teste que o exige, na ordem de trabalho da constituição. Zero mock sobre porta do projeto: quatro fakes in-memory novos em `testsupport/`, e o fake implementa a **mesma** ordenação e o mesmo recorte da versão JPA — é ele que prova o contrato da porta. Factories fluentes novas (`SurveyFactory`, `SurveyVersionFactory`, `QuestionFactory`). E2E com `@E2E`, `DatabaseCleaner` no `@BeforeEach`, **sem** `@Transactional`. Asserção sobre `type` e `code`, nunca sobre mensagem — exceto nos testes de DTO e nos E2E, onde a mensagem é o contrato. As quatro diferenças de SC-007 e as seis escritas bloqueadas de SC-003 são testes explícitos, um por caso. | ✅ |
| **IV. Consistência da superfície pública** | Rotas sem `/api`, aninhadas na aplicação como em 001 (D-15). Nenhum `try/catch` de tradução: `ApiExceptionHandler` segue o ponto único, e os dois erros que carregam coleção (`SurveyNotPublishable` com `impediments`, `CosmeticDeclarationRefused` com `differences`) entram como extensão do corpo RFC 9457, no mesmo molde que `ApiValidationErrorResponse` já usa para `errors`. Uma interface `*Swagger` por controller, com um `@ApiResponse` para **cada** status possível — inclusive os 409 e 422 desta fatia. Saída sempre por presenter, e nunca montada no controller: cada um é `final class` de construtor privado com um `public static R present(O output)`, o único a conhecer o DTO de resposta (D-19). Nenhum DTO de resposta com factory estática. As duas listagens devolvem o `PageResponseDTO<T>` compartilhado, e o controller fixa o media type com `produces` porque `@Schema(implementation)` não expressa o genérico — o detalhe que `ApiKeyController` já registra. Entrada sempre `record` DTO com Bean Validation cuja mensagem espelha a do value object. Criação responde 201 com `Location` — pesquisa, pergunta, regra e versão publicada. Português nas mensagens e `@Schema`, inglês nos campos JSON, nos `code` e nos valores de enum. | ✅ |
| **V. Performance por construção** | Sem N+1: a leitura de versão traz perguntas, opções e regras em uma consulta com `join fetch`, e nenhuma chamada a repositório acontece dentro de laço — reordenar e remover pergunta usam `saveAll`. As duas listagens são paginadas, com padrão 20 e teto 100; não existe caminho que devolva resultado ilimitado. Todo campo de filtro, ordenação ou junção ganha índice na mesma migration: `(application_id, created_at desc, id desc)` em `surveys`, `(survey_id, number desc)` em `survey_versions`, `(version_id, position)` em `questions`, `(question_id, position)` em `question_options`, `(survey_id, occurred_at)` nas transições. Unicidade de negócio é constraint de banco, não só checagem do caso de uso: um rascunho por pesquisa é índice único parcial (D-17), posição de pergunta é `unique` deferrable, opção repetida é `unique`. Sem `synchronized`, sem `ThreadLocal`. Cache não entra — não há problema medido. | ✅ |

**Portões adicionais**: `./mvnw verify` verde; nenhuma migration existente editada (a desta fatia
é nova); OpenAPI completo para os 23 endpoints; `AGENTS.md` e `README.md` atualizados com a
existência do segundo módulo e com a promoção de `ApplicationId` para `core` — é mudança de decisão
arquitetural, e o portão da constituição a exige.

**Violações a justificar**: nenhuma. A seção Complexity Tracking fica vazia.

**Reavaliação pós-Phase 1** (contra a constituição 1.1.0): os artefatos de projeto não
introduzem violação. Os três pontos que mereciam segunda olhada se resolvem dentro das regras
existentes. A extensão de corpo de erro com coleção reusa o mecanismo que
`ApiValidationErrorResponse` já estabeleceu. A promoção de `ApplicationId` é o gatilho do
"segundo caso concreto" que a constituição nomeia, e sobrevive ao critério mais apertado de
1.1.0 — é vocabulário de dois módulos, não tradução interna de um. E o presenter, que a versão
anterior deste plano descrevia como porta em `core`, foi corrigido para a forma vigente: função
estática na infra do módulo. ✅

## Project Structure

### Documentation (this feature)

```text
specs/003-survey-authoring/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output — 19 decisões
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── surveys.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NÃO criado aqui)
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/
│   ├── identity/ApplicationId.java                       ← movido de modules/app (D-02)
│   └── pagination/{Page,PageQuery}.java                   (de 002, reusados sem alteração)
├── infra/http/dtos/PageResponseDTO.java                   (de 002, reusado sem alteração)
├── modules/app/
│   └── ** imports de ApplicationId atualizados **         ~ mecânico, sem mudança de comportamento
└── modules/survey/                                        + módulo novo (D-01)
    ├── domain/
    │   ├── entities/
    │   │   ├── Survey.java  SurveyId.java
    │   │   ├── SurveyLifecycle.java  SurveyState.java
    │   │   ├── SurveyVersion.java  SurveyVersionId.java  SurveyVersionStatus.java
    │   │   ├── Question.java  QuestionId.java  QuestionType.java
    │   │   ├── SurveyStateTransition.java  SurveyStateTransitionId.java
    │   │   ├── TransitionReason.java  ChangeKind.java
    │   │   └── SegmentationRuleId.java  RuleOperation.java
    │   ├── valueobjects/
    │   │   ├── SurveyName.java  QuestionStatement.java  QuestionKey.java
    │   │   ├── QuestionOption.java  ScaleRange.java
    │   │   ├── Trigger.java  EventName.java  TriggerWindow.java  SamplingRate.java
    │   │   └── SegmentationRule.java
    │   └── publication/
    │       ├── PublicationImpediment.java
    │       └── ChangeClassification.java                  verificação de FR-034 (D-11)
    ├── application/
    │   ├── errors/                                        14 erros nomeados
    │   ├── gateways/ApplicationScope.java                 D-03
    │   ├── repositories/
    │   │   ├── SurveyRepository.java
    │   │   ├── SurveyVersionRepository.java
    │   │   └── SurveyStateTransitionRepository.java
    │   └── usecases/                                      23 casos de uso
    │       ├── CreateSurveyUseCase  GetSurveyUseCase  ListSurveysUseCase
    │       ├── RenameSurveyUseCase  DiscardSurveyUseCase
    │       ├── AddQuestionUseCase  UpdateQuestionUseCase
    │       ├── RemoveQuestionUseCase  ReorderQuestionsUseCase
    │       ├── DefineTriggerUseCase
    │       ├── AddSegmentationRuleUseCase  RemoveSegmentationRuleUseCase
    │       ├── CheckSurveyPublicationUseCase  PublishSurveyUseCase
    │       ├── PauseSurveyUseCase  ResumeSurveyUseCase  EndSurveyUseCase
    │       ├── ListStateTransitionsUseCase
    │       ├── OpenSurveyVersionUseCase  DiscardSurveyVersionUseCase
    │       └── GetSurveyVersionUseCase  ListSurveyVersionsUseCase
    │           GetVersionComparabilityUseCase
    └── infra/
        ├── config/UseCasesConfiguration.java              @Bean de cada caso de uso
        ├── gateways/ApplicationScopeGateway.java          delega para modules/app
        ├── database/jpa/
        │   ├── entities/                                  6 entidades JPA
        │   ├── mappers/                                   3 mappers explícitos
        │   └── repositories/                              3 pares porta/adaptador
        └── http/
            ├── controllers/                               5 controllers + 5 interfaces *Swagger
            │   ├── SurveyController  QuestionController
            │   ├── TriggerController  SurveyLifecycleController
            │   └── SurveyVersionController
            ├── dtos/                                      requests, responses e os 2 query DTOs
            └── presenters/                                13 final class, um static present cada

src/main/resources/db/migration/
└── V20260908180000__create_surveys.sql                    + novo

src/test/java/com/renanloureiroo/pitaco/
├── core/identity/ApplicationIdTest.java                   ← movido junto (D-02)
├── testsupport/
│   ├── factories/  SurveyFactory  SurveyVersionFactory  QuestionFactory  TriggerFactory
│   ├── gateways/   InMemoryApplicationScope
│   └── repositories/  InMemorySurveyRepository  InMemorySurveyVersionRepository
│                      InMemorySurveyStateTransitionRepository
└── modules/survey/
    ├── domain/         testes de invariante, derivação de estado, impedimentos, classificação
    ├── application/    um teste por caso de uso, sobre os fakes
    └── infra/http/     testes de constraint dos DTOs + E2E por endpoint
```

**Structure Decision**: `modules/survey` nasce como módulo próprio, ao lado de `modules/app`
(D-01). A pesquisa tem vida fora da aplicação — entrega, coleta e resultado vão pendurar nela —,
o que a distingue da `ApiKey`, que ficou em `modules/app` justamente por não ter. `core` recebe
um único tipo, por gatilho já previsto pela constituição: `ApplicationId`, porque agora dois
módulos o usam (D-02). A paginação não entra na conta — `core/pagination` e o envelope
`PageResponseDTO<T>` chegaram com 002 e são consumidos como estão (D-13).

## Ordem de entrega sugerida

A spec traz seis histórias com prioridade declarada, e o corte natural respeita as dependências
entre elas. A quebra em tarefas é do `/speckit-tasks`; o que o plano fixa é a ordem:

| Fatia | Histórias | O que entrega sozinha |
| --- | --- | --- |
| **0 — preparação** | — | `ApplicationId` em `core`, esqueleto do módulo `survey` |
| **1 — MVP** | US1 + US2 (P1) | um rascunho montável ponta a ponta, com as seis perguntas |
| **2** | US3 (P2) | disparo e regras de segmentação sobre o rascunho |
| **3** | US4 (P2) | publicação, versão 1 congelada, impedimentos |
| **4** | US5 (P3) | pausar, retomar, encerrar, histórico |
| **5** | US6 (P3) | nova versão, classificação e grupos de comparabilidade |

Dentro de cada fatia vale a ordem de trabalho da constituição: value objects e entidade com os
testes de invariante, porta e fake, caso de uso com teste, migration e adaptador JPA, DTO com
teste de constraint, presenter e Swagger e controller, E2E, e por fim o `@Bean`.

A fatia 0 é a única que toca código existente, e o toque é mecânico. Fazê-la antes de tudo evita
que as fatias seguintes nasçam apontando para um caminho que vai mudar.

## Complexity Tracking

> Sem violações da constituição a justificar. A fatia introduz um módulo e um tipo em `core`,
> os dois por gatilho já previsto — o segundo contexto delimitado e o segundo módulo a
> compartilhar o identificador da aplicação. Nenhuma abstração entra por antecipação: os
> presenters são funções estáticas e não beans (D-19), a paginação é consumida de 002 em vez de
> reinventada (D-13), os seis tipos de pergunta são uma tabela em um enum e não uma hierarquia
> (D-09), o disparo é embutido na versão em vez de ganhar tabela (D-14), e o grupo de
> comparabilidade é um inteiro gravado na publicação em vez de um grafo de versões (D-12).
