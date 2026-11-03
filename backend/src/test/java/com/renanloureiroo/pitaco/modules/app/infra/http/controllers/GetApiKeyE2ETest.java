package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApiKeyResponseDTO;
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
@DisplayName("GET /applications/{applicationId}/api-keys/{apiKeyId}")
class GetApiKeyE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;

  @BeforeEach
  void setUp() {
    database.clean();
    application = save(ApplicationFactory.anApplication().build());
  }

  private ApplicationJpaEntity save(Application application) {
    return applications.save(ApplicationJpaMapper.toJpa(application));
  }

  private String uri(String applicationId, String apiKeyId) {
    return "/applications/" + applicationId + "/api-keys/" + apiKeyId;
  }

  private IssueApiKeyResponseDTO issue(String applicationId, String label) {
    return client
        .post()
        .uri("/applications/" + applicationId + "/api-keys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO(label))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(IssueApiKeyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private ApiKeyResponseDTO get(String applicationId, String apiKeyId) {
    return client
        .get()
        .uri(uri(applicationId, apiKeyId))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ApiKeyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Devolve a chave conferida contra o que está gravado no banco")
  void consulta_uma_chave() {
    var issued = issue(application.getId(), "app iOS");

    var found = get(application.getId(), issued.id());
    var stored = apiKeys.findById(issued.id()).orElseThrow();

    assertThat(found.id()).isEqualTo(stored.getId());
    assertThat(found.applicationId()).isEqualTo(stored.getApplicationId());
    assertThat(found.label()).isEqualTo(stored.getLabel());
    assertThat(found.prefix()).isEqualTo(stored.getPrefix());
    assertThat(found.createdAt()).isEqualTo(stored.getCreatedAt());
    assertThat(found.status()).isEqualTo("active");
    assertThat(found.revokedAt()).isNull();
  }

  @Test
  @DisplayName("Encontra a chave revogada, com o estado e o instante da revogação")
  void consulta_uma_chave_revogada() {
    var issued = issue(application.getId(), "app iOS");
    client
        .delete()
        .uri(uri(application.getId(), issued.id()))
        .exchange()
        .expectStatus()
        .isNoContent();

    var found = get(application.getId(), issued.id());

    assertThat(found.status()).isEqualTo("revoked");
    assertThat(found.revokedAt())
        .isEqualTo(apiKeys.findById(issued.id()).orElseThrow().getRevokedAt());
  }

  @Test
  @DisplayName("Chave inexistente, de outra aplicação ou malformada respondem o mesmo 404")
  void recusa_o_que_nao_e_chave_desta_aplicacao() {
    var issued = issue(application.getId(), "app iOS");
    var other = save(ApplicationFactory.anApplication().withSlug("outra-app").build());

    expectNotFound(uri(application.getId(), UUID.randomUUID().toString()), "api_key.not_found");
    expectNotFound(uri(application.getId(), "nao-e-um-id"), "api_key.not_found");
    expectNotFound(uri(other.getId(), issued.id()), "api_key.not_found");
  }

  @Test
  @DisplayName("Aplicação inexistente ou malformada responde application.not_found")
  void recusa_aplicacao_que_nao_existe() {
    var issued = issue(application.getId(), "app iOS");

    expectNotFound(
        uri(UUID.randomUUID().toString(), issued.id()), "application.not_found");
    expectNotFound(uri("nao-e-um-id", issued.id()), "application.not_found");
  }

  @Test
  @DisplayName("Nenhuma resposta carrega segredo ou hash")
  void nunca_devolve_segredo() {
    var issued = issue(application.getId(), "app iOS");
    var stored = apiKeys.findById(issued.id()).orElseThrow();

    var body =
        new String(
            client
                .get()
                .uri(uri(application.getId(), issued.id()))
                .exchange()
                .expectBody()
                .returnResult()
                .getResponseBodyContent());

    assertThat(body).doesNotContain(issued.secret()).doesNotContain(stored.getSecretHash());
    assertThat(body).doesNotContain("secret").doesNotContain("hash");
  }

  @Test
  @DisplayName("Consultar não muda nada no banco, nem nos caminhos de falha")
  void nao_muda_o_estado() {
    var issued = issue(application.getId(), "app iOS");
    var before = apiKeys.findById(issued.id()).orElseThrow();
    var countBefore = apiKeys.count();

    get(application.getId(), issued.id());
    expectNotFound(uri(application.getId(), UUID.randomUUID().toString()), "api_key.not_found");
    expectNotFound(uri(UUID.randomUUID().toString(), issued.id()), "application.not_found");

    var after = apiKeys.findById(issued.id()).orElseThrow();
    assertThat(apiKeys.count()).isEqualTo(countBefore);
    assertThat(after.getRevokedAt()).isEqualTo(before.getRevokedAt());
    assertThat(after.getLabel()).isEqualTo(before.getLabel());
    assertThat(after.getPrefix()).isEqualTo(before.getPrefix());
    assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
    assertThat(after.getSecretHash()).isEqualTo(before.getSecretHash());
  }

  private void expectNotFound(String uri, String code) {
    client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo(code);
  }
}
