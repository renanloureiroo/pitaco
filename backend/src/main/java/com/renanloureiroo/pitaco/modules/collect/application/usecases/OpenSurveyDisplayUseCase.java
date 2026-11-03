package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayIdentifierConflict;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyVersionNotDeliverable;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyDisplayOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenSurveyDisplayUseCase
    implements UseCase<OpenSurveyDisplayUseCase.Input, OpenSurveyDisplayUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final RespondentRepository respondents;
  private final SurveyDisplayRepository displays;

  public OpenSurveyDisplayUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SurveyDisplayRepository displays) {
    this.applications = applications;
    this.catalog = catalog;
    this.respondents = respondents;
    this.displays = displays;
  }

  public record Input(
      String applicationId,
      String displayId,
      String surveyId,
      String versionId,
      Optional<String> respondentReference,
      Optional<String> deviceId,
      Map<String, String> attributes,
      Optional<String> sdkVersion) {}

  // `created` é do ato, não da exibição: é o que separa o 201 da criação do 200 do reenvio.
  public record Output(SurveyDisplayOutput display, boolean created) {}

  @Override
  @Transactional
  public Output execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());
    var displayId = DisplayId.of(input.displayId());
    var surveyId = SurveyId.of(input.surveyId());
    var versionId = SurveyVersionId.of(input.versionId());

    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      throw new ApplicationIsInactive();
    }

    var version =
        catalog
            .publishedVersionOf(versionId, applicationId)
            .filter(published -> published.surveyId().equals(surveyId))
            .orElseThrow(SurveyVersionNotDeliverable::new);

    var existing = displays.findById(displayId, applicationId);
    if (existing.isPresent()) {
      return new Output(SurveyDisplayOutput.of(reopened(existing.get(), surveyId, versionId)), false);
    }

    var identity = RespondentIdentity.of(input.respondentReference(), input.deviceId());
    var now = Instant.now();
    var respondent = respondentOf(applicationId, identity, now);

    var display =
        displays.create(
            SurveyDisplay.create(
                displayId,
                applicationId,
                respondent.id(),
                surveyId,
                versionId,
                version.comparabilityGroup(),
                input.sdkVersion(),
                AttributeSnapshot.of(input.attributes()),
                now));

    log.info(
        "Exibição aberta [{}] application={} survey={} version={}",
        display.id().value(),
        applicationId.value(),
        surveyId.value(),
        versionId.value());

    return new Output(SurveyDisplayOutput.of(display), true);
  }

  // O identificador nasce no dispositivo: reabrir a mesma exibição é o reenvio da fila local, e
  // reusá-lo para outra pesquisa ou versão é conflito (FR-023, D-08).
  private static SurveyDisplay reopened(
      SurveyDisplay existing, SurveyId surveyId, SurveyVersionId versionId) {
    if (!existing.getSurveyId().equals(surveyId) || !existing.getVersionId().equals(versionId)) {
      throw new DisplayIdentifierConflict();
    }
    return existing;
  }

  private Respondent respondentOf(
      ApplicationId applicationId, RespondentIdentity identity, Instant now) {
    return respondents
        .findByIdentity(applicationId, identity)
        .map(
            known -> {
              known.seenAt(now);
              return respondents.update(known);
            })
        .orElseGet(() -> respondents.create(Respondent.create(applicationId, identity, now)));
  }
}
