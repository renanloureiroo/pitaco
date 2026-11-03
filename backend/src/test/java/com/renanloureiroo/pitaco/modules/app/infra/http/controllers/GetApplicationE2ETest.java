package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationRequestDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}")
class GetApplicationE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
  }

  private ApplicationJpaEntity save(Application application) {
    return applications.save(ApplicationJpaMapper.toJpa(application));
  }

  private String uri(String applicationId) {
    return "/applications/" + applicationId;
  }

  private ApplicationResponseDTO get(String uri) {
    return client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ApplicationResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private String rawBody(String uri) {
    return new String(
        client.get().uri(uri).exchange().expectBody().returnResult().getResponseBodyContent());
  }

  @Test
  @DisplayName("Devolve a aplicação com os prazos configurados, batendo com o banco")
  void devolve_a_aplicacao_com_prazos() {
    var saved = save(ApplicationFactory.anApplicationWithPolicies().build());

    var body = get(uri(saved.getId()));

    var stored = applications.findById(saved.getId()).orElseThrow();
    assertThat(body.id()).isEqualTo(stored.getId());
    assertThat(body.slug()).isEqualTo(stored.getSlug());
    assertThat(body.name()).isEqualTo(stored.getName());
    assertThat(body.status()).isEqualTo("active");
    assertThat(body.quietPeriodDays()).isEqualTo(stored.getQuietPeriodDays()).isEqualTo(15);
    assertThat(body.retentionDays()).isEqualTo(stored.getRetentionDays()).isEqualTo(180);
    assertThat(body.openTextRetentionDays())
        .isEqualTo(stored.getOpenTextRetentionDays())
        .isEqualTo(30);
    assertThat(body.createdAt()).isEqualTo(stored.getCreatedAt());
    assertThat(body.updatedAt()).isEqualTo(stored.getUpdatedAt());
  }

  @Test
  @DisplayName("Aplicação sem prazo nenhum responde sem as três chaves de prazo (FR-012)")
  void prazo_nao_configurado_e_chave_ausente() {
    var saved = save(ApplicationFactory.anApplication().build());

    var body = rawBody(uri(saved.getId()));

    assertThat(body)
        .doesNotContain("quietPeriodDays")
        .doesNotContain("retentionDays")
        .doesNotContain("openTextRetentionDays")
        .contains("\"createdAt\"")
        .contains("\"updatedAt\"");
  }

  @Test
  @DisplayName("Aplicação inativa é encontrada, com o estado inactive (FR-014)")
  void aplicacao_inativa_e_encontrada() {
    var saved = save(ApplicationFactory.anApplication().inactive().build());

    assertThat(get(uri(saved.getId())).status()).isEqualTo("inactive");
  }

  @Test
  @DisplayName("O Location da criação abre a consulta e devolve a aplicação criada")
  void o_location_da_criacao_abre_a_consulta() {
    var location =
        client
            .post()
            .uri("/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateApplicationRequestDTO("Acme App", null, 15, null, null))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .exists("Location")
            .returnResult(Void.class)
            .getResponseHeaders()
            .getLocation();

    var body = get(pathOf(location));

    assertThat(body.slug()).isEqualTo("acme-app");
    assertThat(body.quietPeriodDays()).isEqualTo(15);
    assertThat(applications.findById(body.id())).isPresent();
  }

  // O Location vem absoluto e com o context-path; o cliente de teste já responde sob /api.
  private String pathOf(URI location) {
    return location.getPath().replaceFirst("^/api", "");
  }

  @Test
  @DisplayName("Identificador inexistente e malformado respondem o mesmo 404 (FR-013)")
  void recusa_inexistente_e_malformado() {
    expectNotFound(uri(UUID.randomUUID().toString()));
    expectNotFound(uri("nao-e-um-id"));
  }

  @Test
  @DisplayName("Chave de aplicação na superfície administrativa responde 403")
  void recusa_chave_de_aplicacao() {
    var saved = save(ApplicationFactory.anApplication().build());

    client
        .get()
        .uri(uri(saved.getId()))
        .header("X-Pitaco-Key", "pit_qualquer")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");
  }

  @Test
  @DisplayName("Consultar não muda nada no banco, nem nos caminhos de falha (FR-016)")
  void nao_muda_o_estado() {
    var saved = save(ApplicationFactory.anApplicationWithPolicies().build());
    var before = applications.findById(saved.getId()).orElseThrow();
    var countBefore = applications.count();

    get(uri(saved.getId()));
    expectNotFound(uri(UUID.randomUUID().toString()));
    expectNotFound(uri("nao-e-um-id"));

    var after = applications.findById(saved.getId()).orElseThrow();
    assertThat(applications.count()).isEqualTo(countBefore);
    assertThat(after.getName()).isEqualTo(before.getName());
    assertThat(after.getStatus()).isEqualTo(before.getStatus());
    assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    assertThat(after.getQuietPeriodDays()).isEqualTo(before.getQuietPeriodDays());
  }

  private void expectNotFound(String uri) {
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
        .isEqualTo("application.not_found");
  }
}
