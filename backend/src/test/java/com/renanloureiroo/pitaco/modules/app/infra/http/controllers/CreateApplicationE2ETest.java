package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationRequestDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("POST /applications")
class CreateApplicationE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
  }

  @Test
  @DisplayName("Cria a aplicação, devolve 201 com Location e persiste as políticas")
  void cria_uma_aplicacao() {
    var request = new CreateApplicationRequestDTO("Acme App", "acme-app", 15, 180, 30);

    var response =
        client
            .post()
            .uri("/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(CreateApplicationResponseDTO.class)
            .returnResult();

    var body = response.getResponseBody();

    assertThat(body).isNotNull();
    assertThat(body.slug()).isEqualTo("acme-app");
    assertThat(body.id()).isNotBlank();
    assertThat(response.getResponseHeaders().getLocation())
        .asString()
        .endsWith("/applications/" + body.id());

    var saved = applications.findBySlug("acme-app").orElseThrow();

    assertThat(saved.getId()).isEqualTo(body.id());
    assertThat(saved.getName()).isEqualTo("Acme App");
    assertThat(saved.getStatus()).isEqualTo(Status.ACTIVE);
    assertThat(saved.getQuietPeriodDays()).isEqualTo(15);
    assertThat(saved.getRetentionDays()).isEqualTo(180);
    assertThat(saved.getOpenTextRetentionDays()).isEqualTo(30);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("Só com o nome: deriva o slug e deixa as políticas em branco")
  void cria_uma_aplicacao_sem_politicas() {
    var request = new CreateApplicationRequestDTO("Acme App", null, null, null, null);

    client
        .post()
        .uri("/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.slug")
        .isEqualTo("acme-app");

    var saved = applications.findBySlug("acme-app").orElseThrow();

    assertThat(saved.getQuietPeriodDays()).isNull();
    assertThat(saved.getRetentionDays()).isNull();
    assertThat(saved.getOpenTextRetentionDays()).isNull();
  }

  @Test
  @DisplayName("Slug já usado responde 409 sem criar uma segunda aplicação")
  void recusa_slug_duplicado() {
    var request = new CreateApplicationRequestDTO("Acme App", "acme-app", null, null, null);

    client
        .post()
        .uri("/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isCreated();

    client
        .post()
        .uri("/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateApplicationRequestDTO("Outro nome", "acme-app", null, null, null))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.already_exists_with_same_slug")
        .jsonPath("$.detail")
        .value(String.class, detail -> assertThat(detail).contains("acme-app"));

    assertThat(applications.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Retenção de texto livre maior que a geral responde 422")
  void recusa_retencao_de_texto_livre_maior_que_a_geral() {
    var request = new CreateApplicationRequestDTO("Acme App", "acme-app", null, 30, 180);

    client
        .post()
        .uri("/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.open_text_retention_invalid");

    assertThat(applications.count()).isZero();
  }

  @Test
  @DisplayName("Payload inválido responde 400 detalhando cada campo")
  void recusa_payload_invalido() {
    var request = new CreateApplicationRequestDTO("  ", "Acme App", 0, null, null);

    client
        .post()
        .uri("/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors.name")
        .isEqualTo("Nome é obrigatório")
        .jsonPath("$.errors.slug")
        .isEqualTo("Slug aceita apenas minúsculas, dígitos e hífen entre termos")
        .jsonPath("$.errors.quietPeriodDays")
        .isEqualTo("O intervalo de descanso deve ser de ao menos um dia");

    assertThat(applications.count()).isZero();
  }

  @Test
  @DisplayName("JSON malformado responde 400 sem vazar o erro de parsing")
  void recusa_json_malformado() {
    client
        .post()
        .uri("/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"name\":}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
