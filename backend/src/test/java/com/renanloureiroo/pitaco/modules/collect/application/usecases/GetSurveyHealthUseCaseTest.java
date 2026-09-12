package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyNotFoundInApplication;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyHealthOutput.ReasonCount;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryObservedEventRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySuppressionEventRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyHealthUseCase")
class GetSurveyHealthUseCaseTest {

  private static final String EVENT = "checkout.completed";

  private final InMemorySurveyScopeGateway surveys = new InMemorySurveyScopeGateway();
  private final InMemoryPublishedSurveyCatalog catalog = new InMemoryPublishedSurveyCatalog();
  private final InMemorySuppressionEventRepository suppressions =
      new InMemorySuppressionEventRepository();
  private final InMemorySurveyDisplayRepository displays = new InMemorySurveyDisplayRepository();
  private final InMemoryObservedEventRepository events = new InMemoryObservedEventRepository();

  private GetSurveyHealthUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;
  private Instant now;

  @BeforeEach
  void setUp() {
    useCase =
        new GetSurveyHealthUseCase(
            surveys, catalog, suppressions, displays, events, Duration.ofDays(30), 0.05, 5);
    applicationId = ApplicationId.generate();
    surveyId = surveys.aSurveyIn(applicationId);
    versionId = SurveyVersionId.generate();
    now = Instant.now();

    catalog
        .withCurrentPublication(surveyId, versionId, Optional.of(EventName.of(EVENT)))
        .withContent(
            new DeliverableSurvey(
                surveyId,
                versionId,
                1,
                List.of(
                    new DeliverableQuestion(
                        QuestionKey.generate(),
                        1,
                        "Comente",
                        QuestionType.FREE_TEXT,
                        false,
                        List.of(),
                        Optional.empty()))));
  }

  private void suppressed(int times, String sdkVersion, SuppressionReason reason) {
    for (var index = 0; index < times; index++) {
      suppressions.create(
          SuppressionEvent.create(
              applicationId,
              surveyId,
              versionId,
              Optional.empty(),
              Optional.empty(),
              Optional.of(SdkVersion.of(sdkVersion)),
              reason,
              SdkVersion.BASELINE,
              now.minus(Duration.ofHours(1))));
    }
  }

  private void displayed(int times) {
    for (var index = 0; index < times; index++) {
      displays.create(
          SurveyDisplayFactory.aDisplay()
              .forApplication(applicationId)
              .forRespondent(RespondentId.generate())
              .forSurvey(surveyId)
              .forVersion(versionId)
              .openedAt(now.minus(Duration.ofHours(2)))
              .build());
    }
  }

  private GetSurveyHealthUseCase.Input input() {
    return new GetSurveyHealthUseCase.Input(
        applicationId.value(), surveyId.value(), Optional.empty(), Optional.empty());
  }

  @Test
  @DisplayName("Supressão relevante: proporção, contagem por motivo e por versão")
  void supressao_relevante() {
    suppressed(4, "0.9.0", SuppressionReason.UNKNOWN_QUESTION_TYPE);
    suppressed(2, "0.8.0", SuppressionReason.UNSUPPORTED_FEATURE);
    displayed(14);

    var output = useCase.execute(input());

    assertThat(output.displays()).isEqualTo(14);
    assertThat(output.suppressions()).isEqualTo(6);
    assertThat(output.suppressionShare().orElseThrow()).isCloseTo(0.3, within(1e-9));
    assertThat(output.relevant()).isTrue();
    assertThat(output.suppressionsByReason())
        .containsExactly(
            new ReasonCount("unknown_question_type", 4), new ReasonCount("unsupported_feature", 2));
    assertThat(output.suppressionsBySdkVersion())
        .extracting(count -> count.sdkVersion().orElseThrow())
        .containsExactly("0.9.0", "0.8.0");
    assertThat(output.minRequiredVersion()).contains("1.0.0");
  }

  @Test
  @DisplayName("Poucas supressões não são relevantes, ainda que a proporção seja alta")
  void poucas_supressoes() {
    suppressed(2, "0.9.0", SuppressionReason.UNKNOWN_QUESTION_TYPE);

    assertThat(useCase.execute(input()).relevant()).isFalse();
  }

  @Test
  @DisplayName("Evento nunca recebido: nome presente e última ocorrência ausente")
  void evento_nunca_recebido() {
    var output = useCase.execute(input());

    assertThat(output.eventName()).contains(EVENT);
    assertThat(output.eventLastSeenAt()).isEmpty();
    assertThat(output.suppressionShare()).isEmpty();
  }

  @Test
  @DisplayName("Evento já recebido traz a última ocorrência")
  void evento_recebido() {
    var seen = Instant.parse("2026-09-10T10:00:00Z");
    events.withSeen(applicationId, EVENT, seen, seen);

    assertThat(useCase.execute(input()).eventLastSeenAt()).contains(seen);
  }

  @Test
  @DisplayName("Fora do período, supressão e exibição não contam")
  void fora_do_periodo() {
    suppressed(6, "0.9.0", SuppressionReason.UNKNOWN_QUESTION_TYPE);

    var output =
        useCase.execute(
            new GetSurveyHealthUseCase.Input(
                applicationId.value(),
                surveyId.value(),
                Optional.of(now.minus(Duration.ofDays(10))),
                Optional.of(now.minus(Duration.ofDays(5)))));

    assertThat(output.suppressions()).isZero();
  }

  @Test
  @DisplayName("Nunca publicada: sem versão mínima e sem evento")
  void nunca_publicada() {
    var draft = surveys.aSurveyIn(applicationId);

    var output =
        useCase.execute(
            new GetSurveyHealthUseCase.Input(
                applicationId.value(), draft.value(), Optional.empty(), Optional.empty()));

    assertThat(output.minRequiredVersion()).isEmpty();
    assertThat(output.eventName()).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação é 404")
  void outra_aplicacao() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyHealthUseCase.Input(
                        ApplicationId.generate().value(),
                        surveyId.value(),
                        Optional.empty(),
                        Optional.empty())))
        .isInstanceOf(SurveyNotFoundInApplication.class);
  }
}
