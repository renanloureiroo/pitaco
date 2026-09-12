package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.CompetingSurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.PublicationWarningOutput;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryAttributeCatalogGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySdkTrafficGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CheckPublicationWarningsUseCase")
class CheckPublicationWarningsUseCaseTest {

  private static final String EVENT = "checkout.completed";

  private InMemorySurveyVersionRepository versions;
  private InMemorySurveyRepository surveys;
  private InMemoryAttributeCatalogGateway catalog;
  private InMemorySdkTrafficGateway traffic;
  private CheckPublicationWarningsUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    versions = new InMemorySurveyVersionRepository();
    surveys = new InMemorySurveyRepository(versions);
    catalog = new InMemoryAttributeCatalogGateway();
    traffic = new InMemorySdkTrafficGateway();
    useCase =
        new CheckPublicationWarningsUseCase(
            surveys, versions, catalog, traffic, Duration.ofDays(14));
    applicationId = ApplicationId.generate();
  }

  private List<PublicationWarningOutput> warningsOf(Survey survey) {
    return useCase.execute(
        new CheckPublicationWarningsUseCase.Input(applicationId.value(), survey.id().value()));
  }

  private Survey draftListeningTo(String event, SegmentationRule... rules) {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger().forEvent(event))
        .ruledBy(rules)
        .buildSavedIn(versions);
    return survey;
  }

  private Survey liveListeningTo(SurveyFactory factory, TriggerFactory trigger) {
    var survey = factory.buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(trigger)
        .buildPublishedSavedIn(versions);
    return survey;
  }

  @Test
  @DisplayName("Sem regra nem concorrente, nada a avisar")
  void sem_avisos() {
    assertThat(warningsOf(draftListeningTo(EVENT))).isEmpty();
  }

  @Test
  @DisplayName("Regra sobre atributo nunca enviado é avisada com a regra e o atributo")
  void regra_sem_alcance() {
    var rule = SegmentationRule.create("plano", RuleOperation.EQUALS, Optional.of("pro"));
    var survey = draftListeningTo(EVENT, rule);

    assertThat(warningsOf(survey))
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo("segmentation.no_known_match");
              assertThat(warning.ruleId()).contains(rule.id().value());
              assertThat(warning.attribute()).contains("plano");
            });
  }

  @Test
  @DisplayName("Com o valor já visto no catálogo, a regra passa")
  void valor_conhecido_passa() {
    catalog.withAttribute(applicationId, "plano", "free", "pro");
    var survey =
        draftListeningTo(
            EVENT, SegmentationRule.create("plano", RuleOperation.EQUALS, Optional.of("pro")));

    assertThat(warningsOf(survey)).isEmpty();
  }

  @Test
  @DisplayName("Lista as pesquisas no ar e agendadas que escutam o mesmo evento, com a prioridade")
  void lista_as_concorrentes() {
    var ativa =
        liveListeningTo(
            SurveyFactory.aPublishedSurvey()
                .forApplication(applicationId)
                .withName("CSAT do suporte")
                .withPriority(5),
            TriggerFactory.anOpenTrigger().forEvent(EVENT));
    var agendada =
        liveListeningTo(
            SurveyFactory.aPublishedSurvey().forApplication(applicationId).withName("Agendada"),
            TriggerFactory.aScheduledTrigger().forEvent(EVENT));
    liveListeningTo(
        SurveyFactory.aPausedSurvey().forApplication(applicationId),
        TriggerFactory.anOpenTrigger().forEvent(EVENT));
    liveListeningTo(
        SurveyFactory.anEndedSurvey().forApplication(applicationId),
        TriggerFactory.anOpenTrigger().forEvent(EVENT));
    liveListeningTo(
        SurveyFactory.aPublishedSurvey().forApplication(applicationId),
        TriggerFactory.aClosedTrigger().forEvent(EVENT));
    liveListeningTo(
        SurveyFactory.aPublishedSurvey().forApplication(applicationId),
        TriggerFactory.anOpenTrigger().forEvent("outro.evento"));
    liveListeningTo(
        SurveyFactory.aPublishedSurvey().forApplication(ApplicationId.generate()),
        TriggerFactory.anOpenTrigger().forEvent(EVENT));

    var warnings = warningsOf(draftListeningTo(EVENT));

    assertThat(warnings)
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo("trigger.competing_surveys");
              assertThat(warning.competingSurveys())
                  .extracting(CompetingSurveyOutput::surveyId)
                  .containsExactlyInAnyOrder(ativa.id().value(), agendada.id().value());
              assertThat(warning.competingSurveys())
                  .filteredOn(competitor -> competitor.surveyId().equals(ativa.id().value()))
                  .singleElement()
                  .satisfies(
                      competitor -> {
                        assertThat(competitor.name()).isEqualTo("CSAT do suporte");
                        assertThat(competitor.priority()).isEqualTo(5);
                      });
            });
  }

  @Test
  @DisplayName("Uma pesquisa publicada não disputa consigo mesma")
  void nao_disputa_consigo_mesma() {
    var survey =
        liveListeningTo(
            SurveyFactory.aPublishedSurvey().forApplication(applicationId),
            TriggerFactory.anOpenTrigger().forEvent(EVENT));

    assertThat(warningsOf(survey)).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa fora do escopo é não encontrada")
  void fora_do_escopo() {
    var deOutra =
        SurveyFactory.aSurvey().forApplication(ApplicationId.generate()).buildSavedIn(surveys);

    assertThatThrownBy(() -> warningsOf(deOutra)).isInstanceOf(SurveyNotFound.class);
  }

  @Test
  @DisplayName("Maioria do tráfego recente abaixo da versão exigida gera aviso de compatibilidade")
  void maioria_nao_suporta() {
    traffic.withTraffic(applicationId, "0.9.0", 70).withTraffic(applicationId, "1.0.0", 30);

    assertThat(warningsOf(draftListeningTo(EVENT)))
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo("compatibility.unsupported_by_majority");
              assertThat(warning.minRequiredVersion()).contains("1.0.0");
              assertThat(warning.unsupportedShare()).contains(0.7);
            });
  }

  @Test
  @DisplayName("Maioria do tráfego suportando a pesquisa não gera aviso")
  void maioria_suporta() {
    traffic.withTraffic(applicationId, "0.9.0", 30).withTraffic(applicationId, "1.2.0", 70);

    assertThat(warningsOf(draftListeningTo(EVENT))).isEmpty();
  }

  @Test
  @DisplayName("Sem tráfego registrado, nada a dizer sobre compatibilidade")
  void sem_trafego() {
    traffic.withTraffic(ApplicationId.generate(), "0.1.0", 1000);

    assertThat(warningsOf(draftListeningTo(EVENT))).isEmpty();
  }
}
