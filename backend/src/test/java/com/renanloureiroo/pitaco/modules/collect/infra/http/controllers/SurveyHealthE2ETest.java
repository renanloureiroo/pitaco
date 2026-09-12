package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.EVENT;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.KEY_HEADER;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SuppressionRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyHealthResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/health")
class SurveyHealthE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired DatabaseCleaner database;

  private HealthE2ESupport.Tenant tenant;
  private SurveyVersion version;
  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();
    tenant = HealthE2ESupport.tenant(applications, apiKeys, "app-saude");
    version = HealthE2ESupport.publishedSurvey(surveys, versions, tenant.applicationId());
    uri =
        "/applications/"
            + tenant.applicationId().value()
            + "/surveys/"
            + version.getSurveyId().value()
            + "/health";
  }

  private void suppressed(int devices, String sdkVersion) {
    for (var index = 0; index < devices; index++) {
      client
          .post()
          .uri("/collect/suppressions")
          .header(KEY_HEADER, tenant.key())
          .header(VERSION_HEADER, sdkVersion)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new SuppressionRequestDTO(
                  version.getSurveyId().value(),
                  version.id().value(),
                  "unsupported_feature",
                  null,
                  List.of("conditional_display"),
                  null,
                  UUID.randomUUID().toString()))
          .exchange()
          .expectStatus()
          .isAccepted();
    }
  }

  private SurveyHealthResponseDTO health(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyHealthResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Supressões relevantes vêm com motivo, versão do SDK e versão mínima")
  void supressao_relevante() {
    suppressed(4, "0.9.0");
    suppressed(2, "0.8.1");

    var body = health("");

    assertThat(body.displays()).isZero();
    assertThat(body.suppressions().total()).isEqualTo(6);
    assertThat(body.suppressions().byReason())
        .containsExactly(
            new SurveyHealthResponseDTO.ReasonCount("unknown_question_type", 0),
            new SurveyHealthResponseDTO.ReasonCount("unsupported_feature", 6));
    assertThat(body.suppressions().bySdkVersion())
        .containsExactly(
            new SurveyHealthResponseDTO.VersionCount("0.9.0", 4),
            new SurveyHealthResponseDTO.VersionCount("0.8.1", 2));
    assertThat(body.suppressionShare()).isCloseTo(1.0, within(1e-9));
    assertThat(body.relevant()).isTrue();
    assertThat(body.minRequiredVersion()).isEqualTo("1.0.0");
  }

  @Test
  @DisplayName("Evento que nunca chegou é distinguível; depois da primeira consulta, aparece")
  void evento_nunca_recebido() {
    var before = health("");

    assertThat(before.eventName()).isEqualTo(EVENT);
    assertThat(before.eventLastSeenAt()).isNull();
    assertThat(before.relevant()).isFalse();
    assertThat(before.suppressionShare()).isNull();

    client
        .post()
        .uri("/collect/eligibility")
        .header(KEY_HEADER, tenant.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EligibilityRequestDTO(EVENT, new RespondentDTO("u-1", null), null))
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(health("").eventLastSeenAt()).isNotNull();
  }

  @Test
  @DisplayName("Período sem supressão devolve zero")
  void periodo_sem_supressao() {
    suppressed(6, "0.9.0");

    var body = health("?from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z");

    assertThat(body.suppressions().total()).isZero();
    assertThat(body.relevant()).isFalse();
  }

  @Test
  @DisplayName("Período invertido é 400, pesquisa de outra aplicação 404, chave do SDK 403")
  void erros() {
    client
        .get()
        .uri(uri + "?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z")
        .exchange()
        .expectStatus()
        .isBadRequest();

    var other = HealthE2ESupport.tenant(applications, apiKeys, "outra-app");
    client
        .get()
        .uri(
            "/applications/"
                + other.applicationId().value()
                + "/surveys/"
                + version.getSurveyId().value()
                + "/health")
        .exchange()
        .expectStatus()
        .isNotFound();

    client.get().uri(uri).header(KEY_HEADER, tenant.key()).exchange().expectStatus().isForbidden();
  }
}
