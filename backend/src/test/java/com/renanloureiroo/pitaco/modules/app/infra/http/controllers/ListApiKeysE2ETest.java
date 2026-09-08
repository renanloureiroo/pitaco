package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApiKeyJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApiKeyResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyRequestDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/api-keys")
class ListApiKeysE2ETest {

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

  private ApiKeyJpaEntity saveApiKey(String applicationId, int position) {
    return apiKeys.save(
        ApiKeyJpaMapper.toJpa(
            ApiKeyFactory.anApiKey()
                .forApplication(ApplicationId.of(applicationId))
                .withLabel("chave " + position)
                .createdAt(ApiKeyFactory.BATCH_FIRST_CREATED_AT.plusSeconds(position))
                .build()));
  }

  private String uri(String applicationId) {
    return "/applications/" + applicationId + "/api-keys";
  }

  private IssueApiKeyResponseDTO issue(String applicationId, String label) {
    return client
        .post()
        .uri(uri(applicationId))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO(label))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(IssueApiKeyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private void revoke(String applicationId, String apiKeyId) {
    client
        .delete()
        .uri(uri(applicationId) + "/" + apiKeyId)
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private PageResponseDTO<ApiKeyResponseDTO> list(String uri) {
    return client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<ApiKeyResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  private String rawBody(String uri) {
    return new String(
        client.get().uri(uri).exchange().expectBody().returnResult().getResponseBodyContent());
  }

  @Test
  @DisplayName("Lista as chaves da aplicação e bate com o que está gravado no banco")
  void lista_as_chaves_da_aplicacao() {
    var first = issue(application.getId(), "app iOS");
    var second = issue(application.getId(), "app Android");

    var page = list(uri(application.getId()));

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.items()).extracting("id").containsExactlyInAnyOrder(first.id(), second.id());

    var stored = apiKeys.findById(first.id()).orElseThrow();
    var item =
        page.items().stream().filter(each -> each.id().equals(first.id())).findFirst().orElseThrow();
    assertThat(item.applicationId()).isEqualTo(stored.getApplicationId());
    assertThat(item.label()).isEqualTo(stored.getLabel());
    assertThat(item.prefix()).isEqualTo(stored.getPrefix());
    assertThat(item.createdAt()).isEqualTo(stored.getCreatedAt());
    assertThat(item.status()).isEqualTo("active");
    assertThat(item.revokedAt()).isNull();
  }

  @Test
  @DisplayName("Aplicação sem chaves devolve lista vazia com total 0")
  void aplicacao_sem_chaves() {
    var page = list(uri(application.getId()));

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Percorrer as páginas não repete nem omite nenhuma chave (SC-003)")
  void percorre_as_paginas() {
    var issued =
        List.of(
            issue(application.getId(), "chave 1").id(),
            issue(application.getId(), "chave 2").id(),
            issue(application.getId(), "chave 3").id(),
            issue(application.getId(), "chave 4").id(),
            issue(application.getId(), "chave 5").id());

    var first = list(uri(application.getId()) + "?page=0&size=2");
    var second = list(uri(application.getId()) + "?page=1&size=2");
    var third = list(uri(application.getId()) + "?page=2&size=2");
    var beyond = list(uri(application.getId()) + "?page=9&size=2");

    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(beyond.items()).isEmpty();
    assertThat(beyond.total()).isEqualTo(5);

    var traversed =
        java.util.stream.Stream.of(first, second, third)
            .flatMap(page -> page.items().stream())
            .map(item -> item.id())
            .toList();
    assertThat(traversed).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(issued);
  }

  @Test
  @DisplayName("A listagem de uma aplicação não mostra chave de outra (SC-004)")
  void isola_as_aplicacoes() {
    var other = save(ApplicationFactory.anApplication().withSlug("outra-app").build());
    var mine = issue(application.getId(), "minha");
    var theirs = issue(other.getId(), "deles");

    assertThat(list(uri(application.getId())).items()).extracting("id").containsExactly(mine.id());
    assertThat(list(uri(other.getId())).items()).extracting("id").containsExactly(theirs.id());
  }

  @Test
  @DisplayName("Filtra por estado; sem filtro devolve válidas e revogadas")
  void filtra_por_estado() {
    var active = issue(application.getId(), "válida");
    var revoked = issue(application.getId(), "revogada");
    revoke(application.getId(), revoked.id());

    var all = list(uri(application.getId()));
    var onlyActive = list(uri(application.getId()) + "?status=active");
    var onlyRevoked = list(uri(application.getId()) + "?status=revoked");

    assertThat(all.items()).extracting("id").containsExactlyInAnyOrder(active.id(), revoked.id());
    assertThat(onlyActive.items()).extracting("id").containsExactly(active.id());
    assertThat(onlyRevoked.items()).extracting("id").containsExactly(revoked.id());
    assertThat(onlyRevoked.items().getFirst().status()).isEqualTo("revoked");
    assertThat(onlyRevoked.items().getFirst().revokedAt())
        .isEqualTo(apiKeys.findById(revoked.id()).orElseThrow().getRevokedAt());
  }

  @Test
  @DisplayName("Da mais recente para a mais antiga (FR-009)")
  void ordena_da_mais_recente_para_a_mais_antiga() {
    var oldest = saveApiKey(application.getId(), 0);
    var middle = saveApiKey(application.getId(), 1);
    var newest = saveApiKey(application.getId(), 2);

    var page = list(uri(application.getId()));

    assertThat(page.items())
        .extracting("id")
        .containsExactly(newest.getId(), middle.getId(), oldest.getId());
  }

  @Test
  @DisplayName("Aplicação inativa lista normalmente: inatividade impede emitir, não enxergar")
  void aplicacao_inativa_lista() {
    var inactive =
        save(ApplicationFactory.anApplication().withSlug("app-inativa").inactive().build());
    var apiKey = saveApiKey(inactive.getId(), 0);

    client
        .post()
        .uri(uri(inactive.getId()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new IssueApiKeyRequestDTO("recusada"))
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    var page = list(uri(inactive.getId()));

    assertThat(page.items()).extracting("id").containsExactly(apiKey.getId());
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação inexistente e identificador malformado respondem o mesmo 404")
  void recusa_aplicacao_que_nao_existe() {
    expectNotFound(uri(UUID.randomUUID().toString()));
    expectNotFound(uri("nao-e-um-id"));
  }

  @Test
  @DisplayName("Parâmetros de página fora dos limites e estado inválido respondem 400")
  void recusa_parametros_invalidos() {
    expectBadRequest(uri(application.getId()) + "?page=-1", "page");
    expectBadRequest(uri(application.getId()) + "?size=0", "size");
    expectBadRequest(uri(application.getId()) + "?size=101", "size");
    expectBadRequest(uri(application.getId()) + "?status=expirada", "status");
  }

  @Test
  @DisplayName("Nenhuma resposta carrega segredo ou hash (SC-002)")
  void nunca_devolve_segredo() {
    var apiKey = issue(application.getId(), "app iOS");
    var stored = apiKeys.findById(apiKey.id()).orElseThrow();

    var body = rawBody(uri(application.getId()));

    assertThat(body).doesNotContain(apiKey.secret()).doesNotContain(stored.getSecretHash());
    assertThat(body).doesNotContain("secret").doesNotContain("hash");
  }

  @Test
  @DisplayName("Listar não muda nada no banco, nem nos caminhos de falha (SC-006, FR-018)")
  void nao_muda_o_estado() {
    var apiKey = issue(application.getId(), "app iOS");
    var before = apiKeys.findById(apiKey.id()).orElseThrow();
    var revokedAtBefore = before.getRevokedAt();
    var countBefore = apiKeys.count();

    list(uri(application.getId()));
    list(uri(application.getId()) + "?status=revoked");
    expectNotFound(uri(UUID.randomUUID().toString()));
    expectBadRequest(uri(application.getId()) + "?size=0", "size");

    var after = apiKeys.findById(apiKey.id()).orElseThrow();
    assertThat(apiKeys.count()).isEqualTo(countBefore);
    assertThat(after.getRevokedAt()).isEqualTo(revokedAtBefore);
    assertThat(after.getLabel()).isEqualTo(before.getLabel());
    assertThat(after.getPrefix()).isEqualTo(before.getPrefix());
    assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
    assertThat(after.getSecretHash()).isEqualTo(before.getSecretHash());
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

  private void expectBadRequest(String uri, String field) {
    client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors." + field)
        .exists();
  }
}
