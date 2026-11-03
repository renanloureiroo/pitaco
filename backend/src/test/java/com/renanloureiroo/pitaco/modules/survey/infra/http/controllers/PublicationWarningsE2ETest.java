package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository.UsageDelta;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CompetingSurveyDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationWarningDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationWarningsResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/publication-warnings")
class PublicationWarningsE2ETest {

  private static final String EVENT = "checkout.completed";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired JdbcClient jdbc;
  @Autowired SdkVersionUsageRepository usage;
  @Autowired Transactor transactor;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    database.clean();
    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
  }

  private PublicationWarningsResponseDTO warningsOf(Survey survey) {
    return client
        .get()
        .uri(
            "/applications/"
                + applicationId.value()
                + "/surveys/"
                + survey.id().value()
                + "/publication-warnings")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PublicationWarningsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private Survey draftListeningTo(SegmentationRule... rules) {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger().forEvent(EVENT))
        .ruledBy(rules)
        .buildSavedIn(versions);
    return survey;
  }

  private Survey liveListening(SurveyFactory factory) {
    var survey = factory.forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger().forEvent(EVENT))
        .buildPublishedSavedIn(versions);
    return survey;
  }

  @Test
  @DisplayName("Lista as pesquisas no ar que escutam o mesmo evento, deixando a pausada de fora")
  void lista_as_concorrentes() {
    var ativa =
        liveListening(SurveyFactory.aPublishedSurvey().withName("CSAT do suporte").withPriority(5));
    liveListening(SurveyFactory.aPausedSurvey());
    var rascunho = draftListeningTo();

    var warnings = warningsOf(rascunho).warnings();

    assertThat(warnings)
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo("trigger.competing_surveys");
              assertThat(warning.competingSurveys())
                  .containsExactly(new CompetingSurveyDTO(ativa.id().value(), "CSAT do suporte", 5));
            });

    assertThat(warningsOf(ativa).warnings())
        .describedAs("o rascunho não está no ar e não disputa nada")
        .isEmpty();
  }

  @Test
  @DisplayName("Regra sobre valor nunca visto é avisada, e deixa de ser quando o catálogo o conhece")
  void regra_sem_alcance_ate_o_catalogo_conhecer() {
    var rule = SegmentationRule.create("plano", RuleOperation.EQUALS, Optional.of("pro"));
    var survey = draftListeningTo(rule);

    assertThat(warningsOf(survey).warnings())
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo("segmentation.no_known_match");
              assertThat(warning.ruleId()).isEqualTo(rule.id().value());
              assertThat(warning.attribute()).isEqualTo("plano");
              assertThat(warning.competingSurveys()).isNull();
            });

    var attributeId = UUID.randomUUID().toString();
    var now = Instant.now();
    jdbc.sql(
            "insert into application_attributes (id, application_id, name, first_seen_at,"
                + " last_seen_at) values (:id, :app, 'plano', :now, :now)")
        .param("id", attributeId)
        .param("app", applicationId.value())
        .param("now", java.sql.Timestamp.from(now))
        .update();
    jdbc.sql(
            "insert into application_attribute_values (id, attribute_id, value, last_seen_at)"
                + " values (:id, :attribute, 'pro', :now)")
        .param("id", UUID.randomUUID().toString())
        .param("attribute", attributeId)
        .param("now", java.sql.Timestamp.from(now))
        .update();

    assertThat(warningsOf(survey).warnings()).extracting(PublicationWarningDTO::code).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa desconhecida devolve 404")
  void pesquisa_desconhecida() {
    client
        .get()
        .uri(
            "/applications/"
                + applicationId.value()
                + "/surveys/"
                + UUID.randomUUID()
                + "/publication-warnings")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");
  }

  @Test
  @DisplayName("Tráfego recente majoritariamente abaixo da versão exigida gera aviso de compatibilidade")
  void aviso_de_compatibilidade() {
    var now = Instant.now();
    var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    transactor.runInTransaction(
        () -> {
          usage.increment(
              new UsageDelta(applicationId, SdkVersion.of("0.9.0"), today, 80, now, now));
          usage.increment(
              new UsageDelta(applicationId, SdkVersion.of("1.0.0"), today, 20, now, now));
        });

    var warnings = warningsOf(draftListeningTo()).warnings();

    assertThat(warnings)
        .filteredOn(warning -> warning.code().equals("compatibility.unsupported_by_majority"))
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.minRequiredVersion()).isEqualTo("1.0.0");
              assertThat(warning.unsupportedShare()).isEqualTo(0.8);
            });
  }
}
