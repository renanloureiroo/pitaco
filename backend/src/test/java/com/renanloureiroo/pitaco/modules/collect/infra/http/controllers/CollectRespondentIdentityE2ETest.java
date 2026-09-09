package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Identidade do respondente")
class CollectRespondentIdentityE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private Scope primeira;

  private record Scope(ApplicationId applicationId, String key, Survey survey, SurveyVersion version) {}

  @BeforeEach
  void setUp() {
    database.clean();
    primeira = scopeFor("acme-app");
  }

  @Test
  @DisplayName("Duas aberturas só com dispositivo pertencem ao mesmo respondente")
  void duas_aberturas_por_dispositivo() {
    open(primeira, new RespondentDTO(null, DEVICE));
    open(primeira, new RespondentDTO(null, DEVICE));

    assertThat(identities())
        .containsExactly(Map.entry("DEVICE", DEVICE));
    assertThat(countOf("respondents")).isEqualTo(1);
  }

  @Test
  @DisplayName("Quem passa a informar a referência do app vira outro respondente, sem fusão")
  void referencia_do_app_cria_respondente_novo() {
    open(primeira, new RespondentDTO(null, DEVICE));
    open(primeira, new RespondentDTO("u-8f1c", DEVICE));

    assertThat(countOf("respondents")).isEqualTo(2);
    assertThat(identities())
        .containsExactlyInAnyOrder(
            Map.entry("DEVICE", DEVICE), Map.entry("APP_REFERENCE", "u-8f1c"));
  }

  @Test
  @DisplayName("A referência do app prevalece sobre o dispositivo quando as duas vêm juntas")
  void referencia_prevalece() {
    open(primeira, new RespondentDTO("u-8f1c", DEVICE));

    assertThat(identities()).containsExactly(Map.entry("APP_REFERENCE", "u-8f1c"));
  }

  @Test
  @DisplayName("A mesma referência em duas aplicações são dois respondentes independentes")
  void mesma_referencia_em_duas_aplicacoes() {
    var segunda = scopeFor("outra-app");

    open(primeira, new RespondentDTO("u-8f1c", null));
    open(segunda, new RespondentDTO("u-8f1c", null));

    assertThat(countOf("respondents")).isEqualTo(2);
    assertThat(
            jdbc.sql("select count(distinct application_id) from respondents")
                .query(Long.class)
                .single())
        .isEqualTo(2);
  }

  private void open(Scope scope, RespondentDTO respondent) {
    client
        .post()
        .uri("/collect/displays")
        .header(HEADER, scope.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                UUID.randomUUID().toString(),
                scope.survey().id().value(),
                scope.version().id().value(),
                respondent,
                null,
                null))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private Scope scopeFor(String slug) {
    var application = ApplicationFactory.anApplication().withSlug(slug).build();
    applications.save(ApplicationJpaMapper.toJpa(application));

    var issued = ApiKey.issue(application.id(), ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(application.id())
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);

    var version =
        SurveyVersionFactory.aVersion()
            .forSurvey(survey.id())
            .withQuestions(QuestionFactory.aFreeTextQuestion())
            .triggeredBy(TriggerFactory.aTrigger().forEvent("checkout.completed").withRate(1.0))
            .buildPublishedSavedIn(versions);

    return new Scope(application.id(), issued.plainSecret(), survey, version);
  }

  private List<Map.Entry<String, String>> identities() {
    return jdbc
        .sql("select identity_kind, identity_value from respondents order by identity_kind")
        .query((row, index) -> Map.entry(row.getString(1), row.getString(2)))
        .list();
  }

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}
