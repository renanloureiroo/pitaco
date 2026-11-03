package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyRequestDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("POST /applications/{applicationId}/api-keys")
class IssueApiKeyE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
  }

  private ApplicationJpaEntity anApplication() {
    return applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
  }

  private ApplicationJpaEntity anInactiveApplication() {
    return applications.save(
        ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().inactive().build()));
  }

  private String uri(String applicationId) {
    return "/applications/" + applicationId + "/api-keys";
  }

  @Test
  @DisplayName("Emite a chave, devolve 201 com Location e guarda só o hash do segredo")
  void emite_uma_chave() {
    var application = anApplication();

    var response =
        client
            .post()
            .uri(uri(application.getId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new IssueApiKeyRequestDTO("app iOS"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(IssueApiKeyResponseDTO.class)
            .returnResult();

    var body = response.getResponseBody();

    assertThat(body).isNotNull();
    assertThat(body.id()).isNotBlank();
    assertThat(body.applicationId()).isEqualTo(application.getId());
    assertThat(body.label()).isEqualTo("app iOS");
    assertThat(body.prefix()).startsWith("pit_");
    assertThat(body.secret()).startsWith(body.prefix() + "_");
    assertThat(body.createdAt()).isNotNull();
    assertThat(response.getResponseHeaders().getLocation())
        .asString()
        .endsWith(uri(application.getId()) + "/" + body.id());

    var saved = apiKeys.findById(body.id()).orElseThrow();

    assertThat(saved.getApplicationId()).isEqualTo(application.getId());
    assertThat(saved.getLabel()).isEqualTo("app iOS");
    assertThat(saved.getPrefix()).isEqualTo(body.prefix());
    assertThat(saved.getSecretHash()).isEqualTo(ApiKeySecret.hashOf(body.secret()));
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getRevokedAt()).isNull();
  }

  @Test
  @DisplayName("Nenhuma coluna guarda o segredo em claro devolvido na resposta (SC-003)")
  void nao_guarda_o_segredo_em_claro() {
    var application = anApplication();

    var body =
        client
            .post()
            .uri(uri(application.getId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new IssueApiKeyRequestDTO("app iOS"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(IssueApiKeyResponseDTO.class)
            .returnResult()
            .getResponseBody();

    var saved = apiKeys.findById(body.id()).orElseThrow();

    assertThat(saved.getId()).isNotEqualTo(body.secret());
    assertThat(saved.getApplicationId()).isNotEqualTo(body.secret());
    assertThat(saved.getLabel()).isNotEqualTo(body.secret());
    assertThat(saved.getPrefix()).isNotEqualTo(body.secret());
    assertThat(saved.getSecretHash()).isNotEqualTo(body.secret());
    assertThat(body.secret()).doesNotContain(saved.getSecretHash());
  }

  @Test
  @DisplayName("Duas emissões coexistem com identificadores e segredos distintos")
  void emite_duas_chaves() {
    var application = anApplication();

    var first = issue(application.getId());
    var second = issue(application.getId());

    assertThat(first.id()).isNotEqualTo(second.id());
    assertThat(first.secret()).isNotEqualTo(second.secret());
    assertThat(apiKeys.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("Rótulo ausente, vazio ou longo demais responde 400 sem criar chave")
  void recusa_rotulo_invalido() {
    var application = anApplication();

    for (String invalid : new String[] {null, "", "   ", "a".repeat(81)}) {
      client
          .post()
          .uri(uri(application.getId()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(new IssueApiKeyRequestDTO(invalid))
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("request.invalid")
          .jsonPath("$.errors.label")
          .exists();
    }

    assertThat(apiKeys.count()).isZero();
  }

  @Test
  @DisplayName("Aplicação inexistente responde 404 sem criar chave")
  void recusa_aplicacao_inexistente() {
    client
        .post()
        .uri(uri(UUID.randomUUID().toString()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO("app iOS"))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.not_found");

    assertThat(apiKeys.count()).isZero();
  }

  @Test
  @DisplayName("Identificador fora do formato responde 404, não 400 — indistinguível de inexistente")
  void recusa_identificador_malformado_como_inexistente() {
    client
        .post()
        .uri(uri("nao-e-um-id"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO("app iOS"))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.not_found");

    assertThat(apiKeys.count()).isZero();
  }

  @Test
  @DisplayName("Aplicação inativa responde 422 sem criar chave")
  void recusa_aplicacao_inativa() {
    var application = anInactiveApplication();

    client
        .post()
        .uri(uri(application.getId()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO("app iOS"))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.inactive");

    assertThat(apiKeys.count()).isZero();
  }

  @Test
  @DisplayName("JSON malformado responde 400 sem criar chave")
  void recusa_json_malformado() {
    var application = anApplication();

    client
        .post()
        .uri(uri(application.getId()))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"label\":}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(apiKeys.count()).isZero();
  }

  private IssueApiKeyResponseDTO issue(String applicationId) {
    return client
        .post()
        .uri(uri(applicationId))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO("app iOS"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(IssueApiKeyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }
}
