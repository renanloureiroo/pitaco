# Contrato — portas internas de `modules/collect` e o que muda em `app` e `survey`

Assinaturas em Java puro. Nenhum tipo de framework aparece em nada deste arquivo.

---

## 1. Portas de saída de `collect/application/gateways`

### `PublishedSurveyCatalog` — a travessia até a autoria

```java
public interface PublishedSurveyCatalog {

  // Camadas 2 a 4 da FR-011 resolvidas no banco: publicada, no ar, janela aberta, evento exato.
  // Devolve os critérios de segmentação junto, na mesma travessia — nada é buscado em laço.
  List<SurveyCandidate> candidatesFor(ApplicationId applicationId, EventName event, Instant now);

  // Só para a pesquisa escolhida: a versão inteira, perguntas em ordem com opções e faixa.
  Optional<DeliverableSurvey> contentOf(SurveyVersionId versionId);

  // Abertura de exibição: confirma que a versão é publicada e pertence à aplicação da chave.
  Optional<PublishedVersion> publishedVersionOf(SurveyVersionId versionId, ApplicationId applicationId);

  record SurveyCandidate(
      SurveyId surveyId,
      SurveyVersionId versionId,
      int versionNumber,
      int comparabilityGroup,
      SamplingRate rate,
      List<SegmentationCriterion> criteria,
      Instant publishedAt) {}

  record PublishedVersion(
      SurveyId surveyId, SurveyVersionId versionId, int versionNumber, int comparabilityGroup) {}

  record DeliverableSurvey(
      SurveyId surveyId,
      SurveyVersionId versionId,
      int versionNumber,
      List<DeliverableQuestion> questions) {}

  record DeliverableQuestion(
      QuestionKey key,
      int position,
      String statement,
      QuestionType type,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {}
}
```

Adaptador: `collect/infra/gateways/PublishedSurveyCatalogSurvey`, que conhece
`modules/survey` — o único ponto de contato entre os dois módulos.

### `ApplicationScopeGateway` — a aplicação ainda está ativa?

```java
public interface ApplicationScopeGateway {
  Optional<ApplicationScopeState> stateOf(ApplicationId applicationId);
}
```

Idêntica em forma à que `survey` já declara, e deliberadamente **não compartilhada**: a porta
pertence a quem a declara. O enum `ApplicationScopeState` acompanha a porta e também é próprio de
`collect` — o de `survey` vive em `survey/application/gateways` e importá-lo de cá seria o mesmo
acoplamento por outro nome. Adaptador: `collect/infra/gateways/ApplicationScopeGatewayApp`.

---

## 2. Portas de repositório de `collect/application/repositories`

```java
public interface RespondentRepository {
  Optional<Respondent> findByIdentity(ApplicationId applicationId, RespondentIdentity identity);
  Respondent create(Respondent respondent);
  Respondent update(Respondent respondent);
}

public interface SurveyDisplayRepository {
  Optional<SurveyDisplay> findById(DisplayId id, ApplicationId applicationId);
  SurveyDisplay create(SurveyDisplay display);
  SurveyDisplay update(SurveyDisplay display);

  // Uma consulta para todos os candidatos de uma vez. `outcome` vem como está gravado
  // (STARTED, COMPLETED, DISMISSED); ABANDONED é derivado por ResolvedHistory.
  List<DisplayHistoryEntry> historyOf(RespondentId respondentId, List<SurveyId> surveyIds);

  record DisplayHistoryEntry(
      SurveyId surveyId, int comparabilityGroup, DisplayOutcome outcome, Instant openedAt) {}
}

public interface AnswerRepository {
  List<Answer> findByDisplay(DisplayId displayId);
  void saveAll(List<Answer> answers);   // uma escrita em lote, nunca uma por item
}
```

---

## 3. Casos de uso de `collect/application/usecases`

```java
// Sem @Transactional: só lê, e não escreve nada nem no caminho com pesquisa (D-10).
public class FindEligibleSurveyUseCase
    implements UseCase<FindEligibleSurveyUseCase.Input, FindEligibleSurveyUseCase.Output> {

  public record Input(
      String applicationId,
      Optional<String> respondentReference,
      Optional<String> deviceId,
      String event,
      Map<String, String> attributes) {}

  public record Output(Optional<DeliverableSurveyOutput> survey) {}
}

@Transactional  // cria o respondente e a exibição
public class OpenSurveyDisplayUseCase
    implements UseCase<OpenSurveyDisplayUseCase.Input, SurveyDisplayOutput> { … }

@Transactional  // fecha a exibição e grava as respostas
public class SubmitSurveyDisplayUseCase
    implements UseCaseWithoutOutput<SubmitSurveyDisplayUseCase.Input> { … }
```

Todos são POJOs sem anotação de framework, cabeados por `@Bean` em
`collect/infra/config/UseCasesConfiguration`. `@Transactional` é o de `core.transaction`.

`FindEligibleSurveyUseCase` recebe `Duration displayTimeout` e `int maxAttempts` como parâmetros
de construtor, valores simples. Quem lê `CollectProperties` — que é `@ConfigurationProperties`, e
portanto tipo de `infra` — é o `@Bean` que o constrói: o caso de uso não o referencia, sob pena de
trazer Spring para dentro de `application`.

**Ordem das camadas em `FindEligibleSurveyUseCase`** (FR-011), na sequência exata:

1. `ApplicationScopeGateway.stateOf` — inativa devolve vazio (não é erro);
2–4. `PublishedSurveyCatalog.candidatesFor` — no ar, janela, evento;
5. `SurveyDisplayRepository.historyOf` + `ResolvedHistory` — resolvida ou tentativas esgotadas;
6. `SegmentationEvaluation.satisfies` — conjunção dos critérios;
7. `SamplingDecision.accepts` — sorteio determinístico;
depois, o desempate (D-06) e `PublishedSurveyCatalog.contentOf` só para a escolhida.

---

## 4. Erros de `collect/application/errors`

| Classe | `ErrorType` | `code` |
| --- | --- | --- |
| `SurveyVersionNotDeliverable` | `NOT_FOUND` | `survey_version.not_found` |
| `DisplayNotFound` | `NOT_FOUND` | `display.not_found` |
| `DisplayIdentifierConflict` | `CONFLICT` | `display.identifier_conflict` |
| `DisplayAlreadyClosed` | `CONFLICT` | `display.already_closed` |
| `SubmissionRejected` | `BUSINESS_RULE` | `submission.rejected` (com `extensions().errors[]`) |
| `ApplicationIsInactive` (classe própria de `collect`, mesmo molde da homônima de `app`) | `BUSINESS_RULE` | `application.inactive` |

`ApplicationIsInactive` é **reescrita** em `collect/application/errors`, não importada de
`modules/app`: dois módulos nunca se conhecem pelo domínio, e o que os dois compartilham é o
`code` — que é contrato público — e não a classe.

`SubmissionRejected` responde 422 e não 400 porque a entrada está bem-formada: o que falha é a
regra da versão exibida. O `ErrorType.VALIDATION` mapeia para 400 em `ErrorTypeHttpStatus`, então
a classe usa `BUSINESS_RULE` — nenhum atalho é criado na borda, conforme o Princípio IV.

---

## 5. O que muda em `modules/app`

```java
public interface ApiKeyRepository {
  // … o que já existe …

  // Só chave ativa: revogada devolve vazio, sem distinguir de inexistente.
  Optional<ApiKey> findActiveBySecretHash(String secretHash);

  // Update condicional e amortizado: só grava quando o registrado é mais antigo que o limiar.
  void touch(ApiKeyId id, Instant now);
}

public class AuthenticateApiKeyUseCase
    implements UseCase<AuthenticateApiKeyUseCase.Input, AuthenticateApiKeyUseCase.Output> {

  public record Input(String presentedKey) {}
  public record Output(String applicationId, boolean applicationActive) {}
}
```

Novos erros `ApiKeyInvalid` (`UNAUTHORIZED`, `api_key.invalid`), `ApiKeyMissing`
(`UNAUTHORIZED`, `api_key.missing`) e `ApiKeyForbiddenSurface` (`FORBIDDEN`,
`api_key.forbidden_surface`, usado pelo `AdminSurfaceInterceptor`). `ErrorType` já tem
`UNAUTHORIZED` e `FORBIDDEN`, e `ErrorTypeHttpStatus` já os mapeia para 401 e 403 — nenhum
`ErrorType` novo é necessário. Migration acrescenta `api_keys.last_used_at`.

---

## 6. O que muda em `modules/survey`

Apenas movimentação de tipos para `core` (D-16), sem mudança de comportamento:

- `SurveyId`, `SurveyVersionId` → `core/identity`;
- `EventName`, `QuestionKey`, `QuestionType`, `QuestionOption`, `ScaleRange`, `SamplingRate`,
  `RuleOperation` → `core/catalog`;
- `SegmentationRule` permanece em `survey`, com `SegmentationRuleId`, e passa a expor
  `SegmentationCriterion criterion()`.

Nenhum caso de uso, entidade ou endpoint da autoria muda de comportamento; a suíte existente
continua verde como está escrita, apenas com `import` diferente.

---

## 7. Borda compartilhada — `infra/http/security`

```java
public record AuthenticatedApplication(String applicationId, boolean active) {}
```

- `ApiKeyAuthenticationInterceptor` (em `/collect/**`): lê `X-Pitaco-Key`, chama
  `AuthenticateApiKeyUseCase`, guarda o resultado como **atributo da requisição** — nunca
  `ThreadLocal`, por causa das virtual threads.
- `AuthenticatedApplicationArgumentResolver`: injeta `AuthenticatedApplication` no controller.
- `AdminSurfaceInterceptor` (nas rotas administrativas): recusa com `api_key.forbidden_surface`
  quando o header está presente.
- `SecurityWebMvcConfiguration`: registra os dois interceptors e o resolver.
