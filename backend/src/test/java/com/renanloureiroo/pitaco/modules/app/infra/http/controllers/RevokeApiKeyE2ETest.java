package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApiKeyJpaEntity;
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
@DisplayName("DELETE /applications/{applicationId}/api-keys/{apiKeyId}")
class RevokeApiKeyE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
  }

  private IssueApiKeyResponseDTO issue() {
    return client
        .post()
        .uri("/applications/" + application.getId() + "/api-keys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO("app iOS"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(IssueApiKeyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private String uri(String apiKeyId) {
    return "/applications/" + application.getId() + "/api-keys/" + apiKeyId;
  }

  private ApiKeyJpaEntity reload(String apiKeyId) {
    return apiKeys.findById(apiKeyId).orElseThrow();
  }

  @Test
  @DisplayName("Revoga a chave, devolve 204 e marca revoked_at na linha")
  void revoga_uma_chave() {
    var apiKey = issue();

    client.delete().uri(uri(apiKey.id())).exchange().expectStatus().isNoContent();

    assertThat(reload(apiKey.id()).getRevokedAt()).isNotNull();
  }

  @Test
  @DisplayName("A revogação preserva a trilha: label, prefix, created_at e o hash (SC-007)")
  void preserva_a_trilha() {
    var apiKey = issue();
    var before = reload(apiKey.id());
    var label = before.getLabel();
    var prefix = before.getPrefix();
    var createdAt = before.getCreatedAt();
    var secretHash = before.getSecretHash();

    client.delete().uri(uri(apiKey.id())).exchange().expectStatus().isNoContent();

    var after = reload(apiKey.id());
    assertThat(after.getLabel()).isEqualTo(label);
    assertThat(after.getPrefix()).isEqualTo(prefix);
    assertThat(after.getCreatedAt()).isEqualTo(createdAt);
    assertThat(after.getSecretHash()).isEqualTo(secretHash);
    assertThat(after.getRevokedAt()).isNotNull();
  }

  @Test
  @DisplayName("Segunda exclusão responde 409 e não mexe no instante já gravado")
  void recusa_a_segunda_exclusao() {
    var apiKey = issue();

    client.delete().uri(uri(apiKey.id())).exchange().expectStatus().isNoContent();
    var revokedAt = reload(apiKey.id()).getRevokedAt();

    client
        .delete()
        .uri(uri(apiKey.id()))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.already_revoked");

    assertThat(reload(apiKey.id()).getRevokedAt()).isEqualTo(revokedAt);
  }

  @Test
  @DisplayName("Chave inexistente, identificador malformado e chave de outra aplicação: mesmo 404")
  void recusa_o_que_nao_e_chave_desta_aplicacao() {
    var apiKey = issue();
    var otherApplication =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("outra-app").build()));

    expectNotFound(uri(UUID.randomUUID().toString()));
    expectNotFound(uri("nao-e-um-id"));
    expectNotFound(
        "/applications/" + otherApplication.getId() + "/api-keys/" + apiKey.id());

    assertThat(reload(apiKey.id()).getRevokedAt()).isNull();
  }

  @Test
  @DisplayName("Excluir uma chave não toca na outra chave da mesma aplicação")
  void nao_afeta_as_demais_chaves() {
    var revoked = issue();
    var untouched = issue();

    client.delete().uri(uri(revoked.id())).exchange().expectStatus().isNoContent();

    assertThat(reload(revoked.id()).getRevokedAt()).isNotNull();
    assertThat(reload(untouched.id()).getRevokedAt()).isNull();
  }

  private void expectNotFound(String uri) {
    client
        .delete()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.not_found");
  }
}
