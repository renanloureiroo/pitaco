package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.EVENT;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.KEY_HEADER;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkErrorReportResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Relatórios de erro do SDK")
class SdkErrorReportE2ETest {

  private static final String REPORT = "/collect/sdk-errors";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  @Value("${pitaco.collect.rate-limit.sdk-errors.capacity}")
  int sdkErrorCapacity;

  private HealthE2ESupport.Tenant tenant;

  @BeforeEach
  void setUp() {
    database.clean();
    tenant = HealthE2ESupport.tenant(applications, apiKeys, "app-erros");
  }

  private void report(String key, Object body, int status) {
    client
        .post()
        .uri(REPORT)
        .header(KEY_HEADER, key)
        .header(VERSION_HEADER, "1.4.2")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(status);
  }

  private static Map<String, Object> body(String kind, String message) {
    var context = new LinkedHashMap<String, Object>();
    context.put("questionType", "matrix");
    context.put("httpStatus", 502);
    context.put("userEmail", "ana@exemplo.com");
    context.put("stage", "ligue (11) 98765-4321");

    var body = new LinkedHashMap<String, Object>();
    body.put("kind", kind);
    body.put("message", message);
    body.put("context", context);
    body.put("occurredAt", "2026-09-12T10:00:00Z");
    return body;
  }

  private PageResponseDTO<SdkErrorReportResponseDTO> list(ApplicationId owner, String query) {
    return client
        .get()
        .uri("/applications/" + owner.value() + "/sdk-errors" + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<SdkErrorReportResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("202 grava o relatório com contexto sanitizado e mensagem mascarada no banco")
  void grava_sanitizado() {
    report(tenant.key(), body("render_error", "falhou para ana@exemplo.com"), 202);

    var row =
        jdbc.sql(
                "select kind, sdk_version, message, cast(context as text) as context"
                    + " from sdk_error_reports")
            .query()
            .singleRow();

    assertThat(row)
        .containsEntry("kind", "RENDER_ERROR")
        .containsEntry("sdk_version", "1.4.2")
        .containsEntry("message", "falhou para [email]");
    assertThat((String) row.get("context"))
        .contains("\"questionType\": \"matrix\"", "\"httpStatus\": 502")
        .doesNotContain("userEmail", "ana@", "98765");
  }

  @Test
  @DisplayName("A listagem devolve o relatório sanitizado, filtra por tipo e isola aplicações")
  void lista_e_filtra() {
    report(tenant.key(), body("render_error", "a"), 202);
    report(tenant.key(), body("crash", "b"), 202);
    var other = HealthE2ESupport.tenant(applications, apiKeys, "outra-app");
    report(other.key(), body("render_error", "alheio"), 202);

    var all = list(tenant.applicationId(), "");
    var unknown = list(tenant.applicationId(), "?kind=unknown");

    assertThat(all.total()).isEqualTo(2);
    assertThat(all.items())
        .extracting(SdkErrorReportResponseDTO::message)
        .containsExactlyInAnyOrder("a", "b");
    assertThat(all.items().getFirst().context()).containsOnlyKeys("questionType", "httpStatus");
    assertThat(all.items().getFirst().sdkVersion()).isEqualTo("1.4.2");
    assertThat(unknown.items()).extracting(SdkErrorReportResponseDTO::message).containsExactly("b");
  }

  @Test
  @DisplayName("Balde próprio: estourar os relatórios não afeta a elegibilidade da mesma chave")
  void rate_limit_separado() {
    for (var index = 0; index < sdkErrorCapacity; index++) {
      report(tenant.key(), body("network_error", "x"), 202);
    }

    client
        .post()
        .uri(REPORT)
        .header(KEY_HEADER, tenant.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body("network_error", "x"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .exists("Retry-After");

    client
        .post()
        .uri("/collect/eligibility")
        .header(KEY_HEADER, tenant.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EligibilityRequestDTO(EVENT, new RespondentDTO("u-1", null), null))
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  @DisplayName("Sem chave é 401 e corpo malformado é 400, sem gravar")
  void erros_da_superficie_publica() {
    client
        .post()
        .uri(REPORT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body("render_error", "x"))
        .exchange()
        .expectStatus()
        .isUnauthorized();

    client
        .post()
        .uri(REPORT)
        .header(KEY_HEADER, tenant.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"kind\":")
        .exchange()
        .expectStatus()
        .isBadRequest();

    report(tenant.key(), Map.of("occurredAt", "ontem"), 400);

    assertThat(jdbc.sql("select count(*) from sdk_error_reports").query(Long.class).single())
        .isZero();
  }

  @Test
  @DisplayName("Na listagem: filtro inválido é 400, aplicação inexistente 404, chave do SDK 403")
  void erros_da_listagem() {
    client
        .get()
        .uri("/applications/" + tenant.applicationId().value() + "/sdk-errors?kind=crash")
        .exchange()
        .expectStatus()
        .isBadRequest();
    client
        .get()
        .uri("/applications/" + ApplicationId.generate().value() + "/sdk-errors")
        .exchange()
        .expectStatus()
        .isNotFound();
    client
        .get()
        .uri("/applications/" + tenant.applicationId().value() + "/sdk-errors")
        .header(KEY_HEADER, tenant.key())
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
