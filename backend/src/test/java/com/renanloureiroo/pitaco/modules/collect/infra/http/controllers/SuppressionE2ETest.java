package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.KEY_HEADER;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SuppressionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("POST /collect/suppressions")
class SuppressionE2ETest {

  private static final String URI = "/collect/suppressions";
  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";
  private static final String OTHER_DEVICE = "7b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private HealthE2ESupport.Tenant tenant;
  private SurveyVersion version;

  @BeforeEach
  void setUp() {
    database.clean();
    tenant = HealthE2ESupport.tenant(applications, apiKeys, "app-supressao");
    version = HealthE2ESupport.publishedSurvey(surveys, versions, tenant.applicationId());
  }

  private SuppressionRequestDTO request(SurveyVersion target, String device) {
    return new SuppressionRequestDTO(
        target.getSurveyId().value(),
        target.id().value(),
        "unknown_question_type",
        List.of("matrix"),
        null,
        null,
        device);
  }

  private void post(String key, Object body, int status) {
    client
        .post()
        .uri(URI)
        .header(KEY_HEADER, key)
        .header(VERSION_HEADER, "0.9.0")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(status);
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  @DisplayName("202 grava a supressão sem abrir exibição e sem criar respondente")
  void grava_sem_exibicao() {
    post(tenant.key(), request(version, DEVICE), 202);

    var row =
        jdbc.sql(
                "select survey_id, version_id, reason, sdk_version, min_required_version,"
                    + " respondent_id, dedup_key from suppression_events")
            .query()
            .singleRow();

    assertThat(row)
        .containsEntry("survey_id", version.getSurveyId().value())
        .containsEntry("version_id", version.id().value())
        .containsEntry("reason", "UNKNOWN_QUESTION_TYPE")
        .containsEntry("sdk_version", "0.9.0")
        .containsEntry("min_required_version", "1.0.0");
    assertThat(row.get("respondent_id")).isNull();
    assertThat((String) row.get("dedup_key")).hasSize(64).doesNotContain(DEVICE);
    assertThat(count("survey_displays")).isZero();
    assertThat(count("respondents")).isZero();
  }

  @Test
  @DisplayName("A mesma supressão do mesmo dispositivo conta uma vez; outro dispositivo conta")
  void deduplica() {
    post(tenant.key(), request(version, DEVICE), 202);
    post(tenant.key(), request(version, DEVICE), 202);
    post(tenant.key(), request(version, OTHER_DEVICE), 202);

    assertThat(count("suppression_events")).isEqualTo(2);
  }

  @Test
  @DisplayName("Supressões simultâneas do mesmo dispositivo na mesma versão contam uma vez")
  void simultaneas_contam_uma_vez() throws Exception {
    var largada = new CountDownLatch(1);

    try (var pool = Executors.newFixedThreadPool(6)) {
      var envios =
          IntStream.range(0, 6)
              .mapToObj(
                  ignored ->
                      pool.submit(
                          () -> {
                            largada.await();
                            post(tenant.key(), request(version, DEVICE), 202);
                            return null;
                          }))
              .toList();
      largada.countDown();
      for (var envio : envios) {
        envio.get(30, TimeUnit.SECONDS);
      }
    }

    assertThat(count("suppression_events")).isEqualTo(1);
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação responde 202 e não grava nada")
  void outra_aplicacao() {
    var other = HealthE2ESupport.tenant(applications, apiKeys, "outra-app");
    var alheia = HealthE2ESupport.publishedSurvey(surveys, versions, other.applicationId());

    post(tenant.key(), request(alheia, DEVICE), 202);

    assertThat(count("suppression_events")).isZero();
  }

  @Test
  @DisplayName("Motivo fora da lista ou ausente é 400, sem gravar")
  void motivo_invalido() {
    post(
        tenant.key(),
        Map.of(
            "surveyId", version.getSurveyId().value(),
            "versionId", version.id().value(),
            "reason", "crash",
            "deviceId", DEVICE),
        400);
    post(
        tenant.key(),
        Map.of("surveyId", version.getSurveyId().value(), "versionId", version.id().value()),
        400);

    assertThat(count("suppression_events")).isZero();
  }

  @Test
  @DisplayName("JSON malformado é 400")
  void json_malformado() {
    client
        .post()
        .uri(URI)
        .header(KEY_HEADER, tenant.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"surveyId\":")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("Sem chave é 401")
  void sem_chave() {
    client
        .post()
        .uri(URI)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request(version, DEVICE))
        .exchange()
        .expectStatus()
        .isUnauthorized();

    assertThat(count("suppression_events")).isZero();
  }
}
