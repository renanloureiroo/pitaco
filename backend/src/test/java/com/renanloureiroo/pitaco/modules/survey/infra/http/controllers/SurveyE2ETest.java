package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyVersionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.RenameSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("/applications/{applicationId}/surveys")
class SurveyE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyJpaRepository surveys;
  @Autowired SurveyVersionJpaRepository versions;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
  }

  private String uri() {
    return "/applications/" + application.getId() + "/surveys";
  }

  private String uri(String applicationId) {
    return "/applications/" + applicationId + "/surveys";
  }

  private SurveyResponseDTO create(String name) {
    return client
        .post()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO(name))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Cria a pesquisa com 201, Location, e a versão 1 em rascunho no banco")
  void cria_a_pesquisa() {
    var location =
        client
            .post()
            .uri(uri())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateSurveyRequestDTO("NPS pós-checkout"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .exists("Location")
            .expectBody(SurveyResponseDTO.class)
            .returnResult();

    var body = location.getResponseBody();
    assertThat(body.name()).isEqualTo("NPS pós-checkout");
    assertThat(body.state()).isEqualTo("draft");
    assertThat(body.applicationId()).isEqualTo(application.getId());
    assertThat(body.publishedVersionNumber()).isNull();
    assertThat(body.draftVersionNumber()).isEqualTo(1);
    assertThat(location.getResponseHeaders().getLocation().toString()).endsWith(body.id());

    var stored = surveys.findById(body.id()).orElseThrow();
    assertThat(stored.getName()).isEqualTo("NPS pós-checkout");
    assertThat(stored.getLifecycle()).isEqualTo("DRAFT");
    assertThat(stored.getApplicationId()).isEqualTo(application.getId());

    var version = versions.findDraft(body.id()).orElseThrow();
    assertThat(version.getNumber()).isEqualTo(1);
    assertThat(version.getStatus()).isEqualTo("DRAFT");
    assertThat(version.getQuestions()).isEmpty();
    assertThat(version.getTriggerEventName()).isNull();
  }

  @Test
  @DisplayName("Nome em branco devolve 400 e não grava nada")
  void recusa_nome_em_branco() {
    client
        .post()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO("   "))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors.name")
        .isEqualTo("Nome é obrigatório");

    assertThat(surveys.count()).isZero();
    assertThat(versions.count()).isZero();
  }

  @Test
  @DisplayName("Aplicação inexistente e identificador malformado devolvem 404 iguais")
  void recusa_aplicacao_desconhecida() {
    for (var id : new String[] {UUID.randomUUID().toString(), "nao-e-um-id"}) {
      client
          .post()
          .uri(uri(id))
          .contentType(MediaType.APPLICATION_JSON)
          .body(new CreateSurveyRequestDTO("NPS"))
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("application.not_found");
    }

    assertThat(surveys.count()).isZero();
  }

  @Test
  @DisplayName("Aplicação inativa devolve 422 e não grava nada")
  void recusa_aplicacao_inativa() {
    var inativa =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("app-inativa").inactive().build()));

    client
        .post()
        .uri(uri(inativa.getId()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO("NPS"))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.inactive");

    assertThat(surveys.count()).isZero();
    assertThat(versions.count()).isZero();
  }

  @Test
  @DisplayName("JSON malformado devolve 400 sem gravar nada")
  void recusa_json_malformado() {
    client
        .post()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"name\":")
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(surveys.count()).isZero();
  }

  @Test
  @DisplayName("Lista as pesquisas da aplicação, da mais recente para a mais antiga")
  void lista_as_pesquisas() {
    var first = create("Primeira");
    var second = create("Segunda");

    var page = list(uri());

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.items())
        .extracting(SurveyResponseDTO::id)
        .containsExactlyInAnyOrder(first.id(), second.id());
    assertThat(page.items()).extracting(SurveyResponseDTO::state).containsOnly("draft");
  }

  @Test
  @DisplayName("Nenhuma pesquisa de outra aplicação aparece, na listagem nem na consulta")
  void isola_as_aplicacoes() {
    var minha = create("Minha");
    var outra =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("outra-app").build()));

    var page = list(uri(outra.getId()));

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();

    client
        .get()
        .uri(uri(outra.getId()) + "/" + minha.id())
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");
  }

  @Test
  @DisplayName("Aplicação sem pesquisa devolve página vazia com total 0")
  void lista_vazia() {
    var page = list(uri());

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("A travessia de páginas não repete nem omite")
  void percorre_as_paginas() {
    for (var index = 1; index <= 5; index++) {
      create("Pesquisa " + index);
    }

    var first = list(uri() + "?page=0&size=2");
    var second = list(uri() + "?page=1&size=2");
    var third = list(uri() + "?page=2&size=2");

    assertThat(first.items()).hasSize(2);
    assertThat(second.items()).hasSize(2);
    assertThat(third.items()).hasSize(1);
    assertThat(first.total()).isEqualTo(5);
    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(
            java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.items().stream())
                .map(SurveyResponseDTO::id))
        .doesNotHaveDuplicates()
        .hasSize(5);
  }

  @Test
  @DisplayName("Parâmetro de paginação fora dos limites devolve 400")
  void recusa_paginacao_invalida() {
    for (var query : new String[] {"?page=-1", "?size=0", "?size=101"}) {
      client.get().uri(uri() + query).exchange().expectStatus().isBadRequest();
    }
  }

  @Test
  @DisplayName("Consulta devolve a pesquisa com o conteúdo do rascunho")
  void consulta_a_pesquisa() {
    var created = create("NPS pós-checkout");

    var body =
        client
            .get()
            .uri(uri() + "/" + created.id())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyDetailResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(body.id()).isEqualTo(created.id());
    assertThat(body.name()).isEqualTo("NPS pós-checkout");
    assertThat(body.state()).isEqualTo("draft");
    assertThat(body.content().source()).isEqualTo("draft");
    assertThat(body.content().versionNumber()).isEqualTo(1);
    assertThat(body.content().questions()).isEmpty();
    assertThat(body.content().trigger()).isNull();
  }

  @Test
  @DisplayName("Pesquisa inexistente e identificador malformado devolvem 404 iguais")
  void recusa_pesquisa_desconhecida() {
    for (var id : new String[] {UUID.randomUUID().toString(), "nao-e-um-id"}) {
      client
          .get()
          .uri(uri() + "/" + id)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("survey.not_found");
    }
  }

  @Test
  @DisplayName("Renomeia e o novo nome está no banco")
  void renomeia_a_pesquisa() {
    var created = create("Nome antigo");

    var body =
        client
            .patch()
            .uri(uri() + "/" + created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new RenameSurveyRequestDTO("Nome novo"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(body.name()).isEqualTo("Nome novo");
    assertThat(body.state()).isEqualTo("draft");
    assertThat(surveys.findById(created.id()).orElseThrow().getName()).isEqualTo("Nome novo");
  }

  @Test
  @DisplayName("Nome inválido na renomeação devolve 400 e o nome anterior permanece")
  void recusa_renomear_com_nome_invalido() {
    var created = create("Nome antigo");

    client
        .patch()
        .uri(uri() + "/" + created.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new RenameSurveyRequestDTO("   "))
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(surveys.findById(created.id()).orElseThrow().getName()).isEqualTo("Nome antigo");
  }

  @Test
  @DisplayName("Descarta a pesquisa e a versão junto")
  void descarta_a_pesquisa() {
    var created = create("Para descartar");

    client.delete().uri(uri() + "/" + created.id()).exchange().expectStatus().isNoContent();

    assertThat(surveys.findById(created.id())).isEmpty();
    assertThat(versions.findDraft(created.id())).isEmpty();
    assertThat(versions.count()).isZero();
  }

  @Test
  @DisplayName("Descartar fora do escopo da aplicação devolve 404 e nada muda")
  void recusa_descartar_fora_do_escopo() {
    var created = create("Minha");
    var outra =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("outra-app").build()));

    client
        .delete()
        .uri(uri(outra.getId()) + "/" + created.id())
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(surveys.findById(created.id())).isPresent();
  }

  private PageResponseDTO<SurveyResponseDTO> list(String uri) {
    return client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<SurveyResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }
}
