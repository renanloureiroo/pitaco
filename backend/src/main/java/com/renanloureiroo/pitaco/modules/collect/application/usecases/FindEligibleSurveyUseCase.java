package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.SurveyCandidate;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DeliverableSurveyOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.ResolvedHistory;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.SamplingDecision;
import com.renanloureiroo.pitaco.modules.collect.domain.eligibility.SegmentationEvaluation;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Sem @Transactional: só lê, e não escreve nada nem no caminho com pesquisa (D-10, D-18).
@Slf4j
public class FindEligibleSurveyUseCase
    implements UseCase<FindEligibleSurveyUseCase.Input, FindEligibleSurveyUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final RespondentRepository respondents;
  private final SurveyDisplayRepository displays;
  private final Duration displayTimeout;
  private final int maxAttempts;

  // Prazo e limite entram como valores simples: quem lê CollectProperties é o @Bean que constrói
  // este caso de uso, porque a classe de propriedades é tipo de infra.
  public FindEligibleSurveyUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SurveyDisplayRepository displays,
      Duration displayTimeout,
      int maxAttempts) {
    this.applications = applications;
    this.catalog = catalog;
    this.respondents = respondents;
    this.displays = displays;
    this.displayTimeout = displayTimeout;
    this.maxAttempts = maxAttempts;
  }

  public record Input(
      String applicationId,
      Optional<String> respondentReference,
      Optional<String> deviceId,
      String event,
      Map<String, String> attributes) {}

  public record Output(Optional<DeliverableSurveyOutput> survey) {

    static Output empty() {
      return new Output(Optional.empty());
    }
  }

  @Override
  public Output execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());
    var identity = RespondentIdentity.of(input.respondentReference(), input.deviceId());
    var attributes = AttributeSnapshot.of(input.attributes());

    // Camada 1: aplicação inativa não é erro, é silêncio (US1.7, D-19).
    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      return Output.empty();
    }

    // Camadas 2 a 4 numa consulta só: no ar, janela aberta, evento exato.
    var now = Instant.now();
    var candidates = catalog.candidatesFor(applicationId, EventName.of(input.event()), now);

    if (candidates.isEmpty()) {
      return Output.empty();
    }

    // Camada 5: uma consulta de histórico para todos os candidatos de uma vez.
    var history = historyOf(applicationId, identity, candidates, now);

    return candidates.stream()
        .filter(
            candidate ->
                !history.isResolved(candidate.surveyId(), candidate.comparabilityGroup())
                    && !history.exhaustedAttempts(
                        candidate.surveyId(), candidate.comparabilityGroup()))
        .filter(candidate -> SegmentationEvaluation.satisfies(candidate.criteria(), attributes))
        .filter(
            candidate ->
                SamplingDecision.accepts(candidate.surveyId(), identity, candidate.rate()))
        .min(tiebreak())
        .flatMap(chosen -> catalog.contentOf(chosen.versionId()))
        .map(DeliverableSurveyOutput::of)
        .map(FindEligibleSurveyUseCase::delivered)
        .orElseGet(Output::empty);
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

  // Quem nunca abriu exibição não tem linha de respondente, e portanto não tem histórico: o
  // caminho barato continua custando uma consulta só (D-10).
  private ResolvedHistory historyOf(
      ApplicationId applicationId,
      RespondentIdentity identity,
      List<SurveyCandidate> candidates,
      Instant now) {

    return respondents
        .findByIdentity(applicationId, identity)
        .map(
            respondent ->
                displays.historyOf(
                    respondent.id(), candidates.stream().map(SurveyCandidate::surveyId).toList()))
        .map(entries -> ResolvedHistory.of(entries, now, displayTimeout, maxAttempts))
        .orElseGet(() -> ResolvedHistory.of(List.of(), now, displayTimeout, maxAttempts));
  }

  // Vence a que já estava no ar há mais tempo; empate técnico desfeito pelo identificador, para
  // que a mesma consulta repetida devolva sempre a mesma pesquisa (D-06, US1.13).
  private static Comparator<SurveyCandidate> tiebreak() {
    return Comparator.comparing(SurveyCandidate::publishedAt)
        .thenComparing(candidate -> candidate.surveyId().value());
  }
}
