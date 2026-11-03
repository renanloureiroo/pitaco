package com.renanloureiroo.pitaco.infra.http.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Superfície pública — autenticação por chave")
class PublicSurfaceSecurityE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String ELIGIBILITY = "/collect/eligibility";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
  }

  @Test
  @DisplayName("Header ausente devolve 401 api_key.missing")
  void header_ausente_e_401() {
    client
        .post()
        .uri(ELIGIBILITY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request())
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.missing")
        .jsonPath("$.traceId")
        .exists();
  }

  @Test
  @DisplayName("Chave desconhecida e chave revogada devolvem exatamente o mesmo corpo")
  void desconhecida_e_revogada_sao_indistinguiveis() {
    var revoked = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    revoked.apiKey().revoke();
    apiKeys.save(ApiKeyJpaMapper.toJpa(revoked.apiKey()));

    var codeDaRevogada = codeOfUnauthorized(revoked.plainSecret());
    var codeDaDesconhecida = codeOfUnauthorized("pit_00000000_desconhecida");

    assertThat(codeDaRevogada).isEqualTo("api_key.invalid");
    assertThat(codeDaDesconhecida).isEqualTo(codeDaRevogada);
  }

  @Test
  @DisplayName("Chave válida atravessa e registra o último uso")
  void chave_valida_atravessa_e_registra_o_uso() {
    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));

    client
        .post()
        .uri(ELIGIBILITY)
        .header(HEADER, issued.plainSecret())
        .contentType(MediaType.APPLICATION_JSON)
        .body(request())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.survey")
        .doesNotExist();

    var stored = apiKeys.findById(issued.apiKey().id().value()).orElseThrow();

    assertThat(stored.getLastUsedAt()).isNotNull();
  }

  @Test
  @DisplayName("A chave do SDK não vale no painel: 403 api_key.forbidden_surface")
  void chave_publica_em_rota_administrativa_e_403() {
    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));

    client
        .get()
        .uri("/applications/" + applicationId.value() + "/api-keys")
        .header(HEADER, issued.plainSecret())
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");
  }

  private String codeOfUnauthorized(String key) {
    var body =
        client
            .post()
            .uri(ELIGIBILITY)
            .header(HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request())
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody(ProblemBody.class)
            .returnResult()
            .getResponseBody();

    assertThat(body).isNotNull();
    return body.code();
  }

  private record ProblemBody(String code, int status, String detail) {}

  private static EligibilityRequestDTO request() {
    return new EligibilityRequestDTO(
        "checkout.completed", new RespondentDTO("u-8f1c", null), null);
  }
}
