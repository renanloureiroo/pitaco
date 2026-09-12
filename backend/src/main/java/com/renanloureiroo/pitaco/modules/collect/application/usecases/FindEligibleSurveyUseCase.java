package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.SurveyCandidate;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SdkUsageRecorder;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DeliverableSurveyOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedAttributeRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.QuietPeriod;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.ResolvedHistory;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.SamplingDecision;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.SegmentationEvaluation;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Sem @Transactional: a decisão só lê, e não escreve nada nem no caminho com pesquisa (D-10,
// D-18). As únicas escritas são os catálogos de eventos e de atributos, cada um na sua
// transação, ali onde acontece.
@Slf4j
public class FindEligibleSurveyUseCase
    implements UseCase<FindEligibleSurveyUseCase.Input, FindEligibleSurveyUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final RespondentRepository respondents;
  private final SurveyDisplayRepository displays;
  private final ObservedEventRepository events;
  private final ObservedAttributeRepository attributes;
  private final SdkUsageRecorder sdkUsage;
  private final Transactor transactor;
  private final Duration displayTimeout;
  private final int maxAttempts;

  // Prazo e limite entram como valores simples: quem lê CollectProperties é o @Bean que constrói
  // este caso de uso, porque a classe de propriedades é tipo de infra.
  public FindEligibleSurveyUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SurveyDisplayRepository displays,
      ObservedEventRepository events,
      ObservedAttributeRepository attributes,
      SdkUsageRecorder sdkUsage,
      Transactor transactor,
      Duration displayTimeout,
      int maxAttempts) {
    this.applications = applications;
    this.catalog = catalog;
    this.respondents = respondents;
    this.displays = displays;
    this.events = events;
    this.attributes = attributes;
    this.sdkUsage = sdkUsage;
    this.transactor = transactor;
    this.displayTimeout = displayTimeout;
    this.maxAttempts = maxAttempts;
  }

  public record Input(
      String applicationId,
      Optional<String> respondentReference,
      Optional<String> deviceId,
      String event,
      Map<String, String> attributes,
      Optional<String> sdkVersion) {

    public Input {
      sdkVersion = sdkVersion == null ? Optional.empty() : sdkVersion;
    }

    public Input(
        String applicationId,
        Optional<String> respondentReference,
        Optional<String> deviceId,
        String event,
        Map<String, String> attributes) {
      this(applicationId, respondentReference, deviceId, event, attributes, Optional.empty());
    }
  }

  public record Output(Optional<DeliverableSurveyOutput> survey) {

    static Output empty() {
      return new Output(Optional.empty());
    }
  }

  @Override
  public Output execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());
    var identity = RespondentIdentity.of(input.respondentReference(), input.deviceId());
    var snapshot = AttributeSnapshot.of(input.attributes());

    // Camada 1: aplicação inativa não é erro, é silêncio (US1.7, D-19).
    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      return Output.empty();
    }

    var now = Instant.now();
    var event = EventName.of(input.event());

    observe(applicationId, event, snapshot, now);
    recordSdkUsage(applicationId, input.sdkVersion(), now);

    // Camadas 2 a 4 numa consulta só: no ar, janela aberta, evento exato.
    var candidates = catalog.candidatesFor(applicationId, event, now);

    if (candidates.isEmpty()) {
      return Output.empty();
    }

    // Quem nunca abriu exibição não tem linha de respondente, e portanto nem histórico nem
    // descanso: o caminho barato continua custando uma consulta só (D-10).
    var respondent = respondents.findByIdentity(applicationId, identity);

    // Camada 5: uma consulta de histórico para todos os candidatos de uma vez.
    var history = historyOf(respondent, candidates, now);
    var resting = isResting(applicationId, respondent, candidates, now);

    return candidates.stream()
        .filter(
            candidate ->
                !history.isResolved(candidate.surveyId(), candidate.comparabilityGroup())
                    && !history.exhaustedAttempts(
                        candidate.surveyId(), candidate.comparabilityGroup()))
        .filter(candidate -> !resting || candidate.ignoresQuietPeriod())
        .filter(candidate -> SegmentationEvaluation.satisfies(candidate.criteria(), snapshot))
        .filter(
            candidate ->
                SamplingDecision.accepts(candidate.surveyId(), identity, candidate.rate()))
        .min(tiebreak())
        .flatMap(chosen -> catalog.contentOf(chosen.versionId()))
        .map(DeliverableSurveyOutput::of)
        .map(FindEligibleSurveyUseCase::delivered)
        .orElseGet(Output::empty);
  }

  // Os catálogos alimentam a autoria, e a decisão de entrega não depende deles: uma falha ao
  // gravar vira aviso, nunca resposta de erro ao SDK. Transações separadas para que a falha de
  // um não desfaça o outro.
  private void observe(
      ApplicationId applicationId, EventName event, AttributeSnapshot snapshot, Instant now) {
    safely("Evento observado", applicationId, () -> events.record(applicationId, event, now));

    if (!snapshot.values().isEmpty()) {
      safely(
          "Atributos observados",
          applicationId,
          () -> attributes.record(applicationId, snapshot, now));
    }
  }

  // Versão fora do formato é ignorada: a consulta nunca é recusada por causa dela. Sem transação,
  // porque quem implementa acumula em memória e descarrega depois.
  private void recordSdkUsage(ApplicationId applicationId, Optional<String> raw, Instant now) {
    raw.flatMap(SdkVersion::parse)
        .ifPresent(
            version -> {
              try {
                sdkUsage.record(applicationId, version, now);
              } catch (RuntimeException failure) {
                log.warn(
                    "Versão do SDK não registrada application={} motivo={}",
                    applicationId.value(),
                    failure.getClass().getSimpleName());
              }
            });
  }

  private void safely(String what, ApplicationId applicationId, Runnable work) {
    try {
      transactor.runInTransaction(work);
    } catch (RuntimeException failure) {
      log.warn(
          "{} não registrado application={} motivo={}",
          what,
          applicationId.value(),
          failure.getClass().getSimpleName());
    }
  }

  // Só a entrega é registrada, com identificadores e nada mais: o caminho vazio é a maioria
  // absoluta do tráfego e não vira uma linha de log por evento do app hospedeiro.
  private static Output delivered(DeliverableSurveyOutput survey) {
    log.info(
        "Pesquisa entregue survey={} version={} perguntas={}",
        survey.surveyId(),
        survey.versionId(),
        survey.questions().size());

    return new Output(Optional.of(survey));
  }

  private ResolvedHistory historyOf(
      Optional<Respondent> respondent, List<SurveyCandidate> candidates, Instant now) {
    return respondent
        .map(
            known ->
                displays.historyOf(
                    known.id(), candidates.stream().map(SurveyCandidate::surveyId).toList()))
        .map(entries -> ResolvedHistory.of(entries, now, displayTimeout, maxAttempts))
        .orElseGet(() -> ResolvedHistory.of(List.of(), now, displayTimeout, maxAttempts));
  }

  // Camada do descanso, entre o histórico e a segmentação. Só consulta alguma coisa quando pode
  // barrar alguém: respondente conhecido, candidato não isento e intervalo configurado.
  private boolean isResting(
      ApplicationId applicationId,
      Optional<Respondent> respondent,
      List<SurveyCandidate> candidates,
      Instant now) {
    if (respondent.isEmpty()
        || candidates.stream().allMatch(SurveyCandidate::ignoresQuietPeriod)) {
      return false;
    }

    return applications
        .quietPeriodDaysOf(applicationId)
        .map(QuietPeriod::ofDays)
        .flatMap(
            period ->
                displays
                    .lastOpenedAt(respondent.get().id())
                    .map(last -> period.isRestingAt(last, now)))
        .orElse(false);
  }

  // A prioridade explícita pesa antes de tudo; depois vence a que já estava no ar há mais
  // tempo, e o identificador desfaz o empate técnico — a mesma consulta repetida devolve sempre
  // a mesma pesquisa (D-06, US1.13).
  private static Comparator<SurveyCandidate> tiebreak() {
    return Comparator.<SurveyCandidate>comparingInt(SurveyCandidate::priority)
        .reversed()
        .thenComparing(SurveyCandidate::publishedAt)
        .thenComparing(candidate -> candidate.surveyId().value());
  }
}
