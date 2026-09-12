package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SuppressionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.RequiredSdk;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionDedupKey;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Tudo que não pode virar supressão é descartado em silêncio, com a mesma resposta de quando é
// gravado: pesquisa de outra aplicação, versão que não é publicada, aplicação inativa. Recusar
// com 404 diria ao portador de uma chave extraída do bundle que aquele identificador existe em
// algum lugar.
@Slf4j
public class RecordSuppressionUseCase
    implements UseCaseWithoutOutput<RecordSuppressionUseCase.Input> {

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final RespondentRepository respondents;
  private final SuppressionEventRepository suppressions;
  private final Duration dedupWindow;

  public RecordSuppressionUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SuppressionEventRepository suppressions,
      Duration dedupWindow) {
    this.applications = applications;
    this.catalog = catalog;
    this.respondents = respondents;
    this.suppressions = suppressions;
    this.dedupWindow = dedupWindow;
  }

  public record Input(
      String applicationId,
      String surveyId,
      String versionId,
      SuppressionReason reason,
      Optional<String> respondentReference,
      Optional<String> deviceId,
      Optional<String> sdkVersion) {}

  @Override
  @Transactional
  public void execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());

    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      discarded(applicationId, "aplicacao_inativa");
      return;
    }

    var ids = idsOf(input);
    var published =
        ids.flatMap(pair -> catalog.publishedVersionOf(pair.versionId(), applicationId))
            .filter(version -> ids.get().surveyId().equals(version.surveyId()));

    if (published.isEmpty()) {
      discarded(applicationId, "fora_do_escopo");
      return;
    }

    var version = published.get();
    var now = Instant.now();
    var identity = identityOf(input);
    var dedupKey = identity.map(known -> SuppressionDedupKey.of(applicationId, known));

    if (dedupKey.isPresent()) {
      suppressions.lockDedup(version.versionId(), dedupKey.get());

      if (suppressions.existsSince(version.versionId(), dedupKey.get(), now.minus(dedupWindow))) {
        discarded(applicationId, "repetida");
        return;
      }
    }

    // Só associa quem já existe: supressão não é contato, e não cria respondente.
    var respondentId =
        identity
            .flatMap(known -> respondents.findByIdentity(applicationId, known))
            .map(Respondent::id);

    var minRequired =
        catalog.contentOf(version.versionId()).map(RequiredSdk::of).orElse(SdkVersion.BASELINE);

    var event =
        suppressions.create(
            SuppressionEvent.create(
                applicationId,
                version.surveyId(),
                version.versionId(),
                respondentId,
                dedupKey,
                input.sdkVersion().flatMap(SdkVersion::parse),
                input.reason(),
                minRequired,
                now));

    log.info(
        "Supressão registrada survey={} version={} motivo={} versaoMinima={}",
        event.getSurveyId().value(),
        event.getVersionId().value(),
        event.getReason(),
        event.getMinRequiredVersion().value());
  }

  private record Ids(SurveyId surveyId, SurveyVersionId versionId) {}

  private static Optional<Ids> idsOf(Input input) {
    try {
      return Optional.of(
          new Ids(SurveyId.of(input.surveyId()), SurveyVersionId.of(input.versionId())));
    } catch (DomainException malformed) {
      return Optional.empty();
    }
  }

  // Sem identificação, a supressão é contada sem deduplicação: o SDK sempre manda o
  // dispositivo, e a ausência só acontece fora do contrato.
  private static Optional<RespondentIdentity> identityOf(Input input) {
    try {
      return Optional.of(RespondentIdentity.of(input.respondentReference(), input.deviceId()));
    } catch (DomainException unidentified) {
      return Optional.empty();
    }
  }

  private static void discarded(ApplicationId applicationId, String reason) {
    log.info("Supressão descartada application={} motivo={}", applicationId.value(), reason);
  }
}
