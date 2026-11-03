package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.EVENT;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.KEY_HEADER;
import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository.UsageDelta;
import com.renanloureiroo.pitaco.modules.collect.infra.health.BufferedSdkUsageRecorder;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkVersionsResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/sdk-versions")
class SdkVersionsE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired BufferedSdkUsageRecorder recorder;
  @Autowired SdkVersionUsageRepository usage;
  @Autowired Transactor transactor;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private HealthE2ESupport.Tenant tenant;

  @BeforeEach
  void setUp() {
    recorder.flush();
    database.clean();
    tenant = HealthE2ESupport.tenant(applications, apiKeys, "app-versoes");
  }

  private void consult(String key, String version) {
    var request =
        client
            .post()
            .uri("/collect/eligibility")
            .header(KEY_HEADER, key)
            .contentType(MediaType.APPLICATION_JSON);
    if (version != null) {
      request = request.header(VERSION_HEADER, version);
    }
    request
        .body(new EligibilityRequestDTO(EVENT, new RespondentDTO("u-1", null), null))
        .exchange()
        .expectStatus()
        .isOk();
  }

  private SdkVersionsResponseDTO versionsOf(ApplicationId applicationId) {
    return client
        .get()
        .uri("/applications/" + applicationId.value() + "/sdk-versions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SdkVersionsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("A versão do cabeçalho da elegibilidade vira distribuição, gravada em lote")
  void distribuicao_das_versoes() {
    consult(tenant.key(), "1.1.0");
    consult(tenant.key(), "1.1.0");
    consult(tenant.key(), "1.1.0");
    consult(tenant.key(), "1.0.0+build.7");

    assertThat(jdbc.sql("select count(*) from sdk_version_usage").query(Long.class).single())
        .isZero();

    recorder.flush();

    var body = versionsOf(tenant.applicationId());

    assertThat(body.recentRequests()).isEqualTo(4);
    assertThat(body.versions())
        .extracting(SdkVersionsResponseDTO.Version::version)
        .containsExactly("1.1.0", "1.0.0");
    var newest = body.versions().getFirst();
    assertThat(newest.requestCount()).isEqualTo(3);
    assertThat(newest.recentShare()).isCloseTo(0.75, within(1e-9));
    assertThat(newest.stale()).isFalse();
    assertThat(
            jdbc.sql(
                    "select request_count from sdk_version_usage where version = '1.1.0'"
                        + " and application_id = :app")
                .param("app", tenant.applicationId().value())
                .query(Long.class)
                .single())
        .isEqualTo(3);
  }

  @Test
  @DisplayName("Versão malformada no cabeçalho é ignorada, e a consulta responde 200")
  void versao_malformada() {
    consult(tenant.key(), "latest");
    consult(tenant.key(), null);
    recorder.flush();

    assertThat(versionsOf(tenant.applicationId()).versions()).isEmpty();
  }

  @Test
  @DisplayName("Versão sem contato além do limite aparece como sumida do tráfego")
  void versao_sumida() {
    var longAgo = Instant.now().minus(Duration.ofDays(40));
    transactor.runInTransaction(
        () ->
            usage.increment(
                new UsageDelta(
                    tenant.applicationId(),
                    SdkVersion.of("0.9.0"),
                    LocalDate.ofInstant(longAgo, ZoneOffset.UTC),
                    50,
                    longAgo,
                    longAgo)));

    var version = versionsOf(tenant.applicationId()).versions().getFirst();

    assertThat(version.version()).isEqualTo("0.9.0");
    assertThat(version.stale()).isTrue();
    assertThat(version.requestCount()).isEqualTo(50);
    assertThat(version.recentRequestCount()).isZero();
  }

  @Test
  @DisplayName("Não mostra versão vista por outra aplicação")
  void isola_aplicacoes() {
    var other = HealthE2ESupport.tenant(applications, apiKeys, "outra-app");
    consult(other.key(), "2.0.0");
    recorder.flush();

    assertThat(versionsOf(tenant.applicationId()).versions()).isEmpty();
    assertThat(versionsOf(other.applicationId()).versions()).hasSize(1);
  }

  @Test
  @DisplayName("Aplicação inexistente é 404 e chave do SDK na superfície administrativa é 403")
  void erros() {
    client
        .get()
        .uri("/applications/" + ApplicationId.generate().value() + "/sdk-versions")
        .exchange()
        .expectStatus()
        .isNotFound();

    client
        .get()
        .uri("/applications/" + tenant.applicationId().value() + "/sdk-versions")
        .header(KEY_HEADER, tenant.key())
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
