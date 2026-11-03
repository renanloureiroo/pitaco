# Contrato interno — portas, projeções e casos de uso

**Feature**: `006-collection-read-list` · Módulo: `collect`

Assinaturas com a semântica que cada implementação — JPA e fake — precisa cumprir. É este
documento que o fake de `testsupport` e o adaptador JPA têm de honrar igualmente: o teste de caso
de uso só vale se o fake se comportar como o banco.

---

## 1. `SurveyDisplayRepository` — membros novos

```java
// items ordenado por openedAt desc com desempate por id desc, recortado em page*size; total conta
// o conjunto inteiro que atende ao filtro. Página além do fim devolve items vazio, nunca erro.
// Filtro ausente (Optional.empty) não restringe. O período é inclusivo nos dois extremos.
Page<DisplaySummary> findPage(ListDisplaysQuery query);

Page<RespondentDisplaySummary> findPageByRespondent(ListRespondentDisplaysQuery query);

record DisplaySummary(
    DisplayId id,
    SurveyVersionId versionId,
    int versionNumber,
    int comparabilityGroup,
    DisplayOutcome outcome,
    Optional<String> sdkVersion,
    Instant openedAt,
    Optional<Instant> closedAt) {}

record RespondentDisplaySummary(SurveyId surveyId, DisplaySummary display) {}

record ListDisplaysQuery(
    ApplicationId applicationId,
    SurveyId surveyId,
    Optional<SurveyVersionId> versionId,
    Optional<DisplayOutcome> outcome,
    Optional<Instant> openedFrom,
    Optional<Instant> openedTo,
    int page,
    int size) implements PageQuery {}

record ListRespondentDisplaysQuery(
    ApplicationId applicationId,
    RespondentId respondentId,
    Optional<DisplayOutcome> outcome,
    Optional<Instant> openedFrom,
    Optional<Instant> openedTo,
    int page,
    int size) implements PageQuery {}
```

**Obrigações da implementação**

- `applicationId` restringe sempre, mesmo quando a rota já garantiu o escopo: é o que fecha
  FR-034 em uma camada só.
- `versionNumber` vem de junção com `survey_versions`; **uma junção por página**, nunca uma por
  linha.
- Nenhum atributo de exibição é carregado: a projeção não os tem, e é por isso que existe (D-02).
- `findById(DisplayId, ApplicationId)`, já existente, continua devolvendo a entidade completa e
  serve a consulta individual.

---

## 2. `RespondentRepository` — membros novos

```java
Optional<Respondent> findById(RespondentId id, ApplicationId applicationId);

// items ordenado por lastSeenAt desc com desempate por id desc.
Page<Respondent> findPage(ListRespondentsQuery query);

record ListRespondentsQuery(ApplicationId applicationId, int page, int size)
    implements PageQuery {}
```

Aqui a página devolve a **entidade**, e não projeção: `Respondent` não tem coleção associada, então
não há N+1 a evitar e não há motivo para projetar.

---

## 3. `AnswerRepository` — alteração de semântica

```java
// As opções vêm na mesma consulta, em join fetch: uma consulta para a exibição inteira.
List<Answer> findByDisplay(DisplayId displayId);
```

A assinatura não muda; a consulta JPA passa a trazer `options` em `join fetch` em vez de deixar o
`@ElementCollection(fetch = EAGER)` disparar uma consulta por resposta. É o que impede o N+1 na
consulta individual — a única mudança de comportamento que esta fatia faz em código já existente.

---

## 4. `SurveyScopeGateway` — porta nova

```java
package ...modules.collect.application.gateways;

// A travessia até a autoria só para existência: a pesquisa que nunca publicou existe, não tem
// exibição, e por isso não pode ser confundida com pesquisa inexistente (D-04).
public interface SurveyScopeGateway {

  boolean existsInApplication(SurveyId surveyId, ApplicationId applicationId);
}
```

Adaptador `SurveyScopeGatewaySurvey` em `collect/infra/gateways`, sobre `SurveyScopeJpaRepository`
— uma `JpaRepository` própria de `collect` sobre `SurveyJpaEntity`, mesmo molde de
`PublishedSurveyJpaRepository`.

---

## 5. `ApplicationScopeGateway` — método novo

```java
// Prazo efetivo de retenção de texto livre: o específico, ou o geral quando ele não existe.
// Ausente significa sem expiração (D-07).
Optional<Integer> effectiveOpenTextRetentionDaysOf(ApplicationId applicationId);
```

Servido por `Application.effectiveOpenTextRetentionDays()`, que já existe e até aqui nunca foi
usada. `stateOf` **não muda** — é caminho quente da elegibilidade (D-08).

---

## 6. `CollectScope` — serviço de aplicação novo

```java
package ...modules.collect.application.services;

// Mesmo papel de SurveyScope na autoria: concentra a checagem de escopo que três casos de uso
// repetiriam, e é onde o erro nomeado é lançado.
final class CollectScope {
  static ApplicationId existingApplicationIdOf(ApplicationScopeGateway, String rawId);
  static SurveyId existingSurveyIdOf(SurveyScopeGateway, ApplicationId, String rawId);
  static Respondent existingRespondentOf(RespondentRepository, ApplicationId, String rawId);
}
```

---

## 7. Casos de uso

Todos implementam `UseCase<Input, Output>` de `core.usecase`, sem anotação de framework, cabeados
por `@Bean` no `UseCasesConfiguration` de `collect`. Nenhum declara `@Transactional` (D-13).

| Caso de uso | Consultas | Recusa |
| --- | --- | --- |
| `ListSurveyDisplaysUseCase` | existência da pesquisa · página · contagem | `survey.not_found` |
| `GetSurveyDisplayUseCase` | exibição · respostas com opções · conteúdo da versão · retenção da aplicação | `display.not_found` |
| `ListRespondentsUseCase` | existência da aplicação · página · contagem | `application.not_found` |
| `ListRespondentDisplaysUseCase` | existência do respondente · página · contagem | `respondent.not_found` |

**Input** e **Output** são `record` aninhados na própria classe. O `Output` das listagens segue o
molde já usado por `ListSurveysUseCase`: `items`, `page`, `size`, `total`, `totalPages`.

Cada caso de uso loga **sucesso** via `@Slf4j`, com identificador e total — nunca com
`identityValue`, nem com conteúdo de resposta, nem com valor de atributo (FR-030). Erro é logado
uma vez só, na borda.

---

## 8. Fakes de `testsupport` — obrigações

Os fakes já existem e ganham os membros novos. Um fake que não reproduzir a semântica abaixo
deixa o teste de caso de uso verde enquanto o banco quebra:

| Fake | Precisa reproduzir |
| --- | --- |
| `InMemorySurveyDisplayRepository` | ordenação `openedAt` desc + `id` desc; recorte por `page*size`; `total` do conjunto filtrado; período **inclusivo**; filtro ausente não restringe; escopo por `applicationId` |
| `InMemoryRespondentRepository` | ordenação `lastSeenAt` desc + `id` desc; recorte e total; escopo por `applicationId` |
| `InMemoryCollectApplicationScopeGateway` | `effectiveOpenTextRetentionDaysOf`, incluindo o caso ausente |
| `InMemorySurveyScopeGateway` (novo) | existência por par `(surveyId, applicationId)` |
| `InMemoryAnswerRepository` | `findByDisplay` devolvendo as respostas com suas opções |
