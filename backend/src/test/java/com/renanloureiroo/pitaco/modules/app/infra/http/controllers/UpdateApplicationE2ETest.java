package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationResponseDTO;
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
@DisplayName("PATCH /applications/{applicationId}")
class UpdateApplicationE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(
            ApplicationJpaMapper.toJpa(ApplicationFactory.anApplicationWithPolicies().build()));
  }

  private String uri() {
    return "/applications/" + application.getId();
  }

  private ApplicationJpaEntity reload() {
    return applications.findById(application.getId()).orElseThrow();
  }

  @Test
  @DisplayName("Altera o nome e os prazos, responde a aplicação inteira e persiste")
  void altera_nome_e_prazos() {
    var body =
        client
            .patch()
            .uri(uri())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"name\":\"Acme Brasil\",\"quietPeriodDays\":7,\"retentionDays\":365}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ApplicationResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(body).isNotNull();
    assertThat(body.name()).isEqualTo("Acme Brasil");
    assertThat(body.slug()).isEqualTo("acme-app");
    assertThat(body.quietPeriodDays()).isEqualTo(7);
    assertThat(body.retentionDays()).isEqualTo(365);
    assertThat(body.openTextRetentionDays()).isEqualTo(30);

    var saved = reload();
    assertThat(saved.getName()).isEqualTo("Acme Brasil");
    assertThat(saved.getQuietPeriodDays()).isEqualTo(7);
    assertThat(saved.getRetentionDays()).isEqualTo(365);
    assertThat(saved.getOpenTextRetentionDays()).isEqualTo(30);
    assertThat(saved.getUpdatedAt()).isAfter(application.getUpdatedAt());
  }

  @Test
  @DisplayName("null remove o prazo e o campo some da resposta; campo omitido fica como estava")
  void nulo_remove_e_omitido_preserva() {
    client
        .patch()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"quietPeriodDays\":null}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.quietPeriodDays")
        .doesNotExist()
        .jsonPath("$.retentionDays")
        .isEqualTo(180)
        .jsonPath("$.name")
        .isEqualTo("Acme App");

    var saved = reload();
    assertThat(saved.getQuietPeriodDays()).isNull();
    assertThat(saved.getRetentionDays()).isEqualTo(180);
  }

  @Test
  @DisplayName("Corpo vazio é aceito e não muda nada")
  void corpo_vazio_nao_muda_nada() {
    client
        .patch()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{}")
        .exchange()
        .expectStatus()
        .isOk();

    var saved = reload();
    assertThat(saved.getName()).isEqualTo("Acme App");
    assertThat(saved.getQuietPeriodDays()).isEqualTo(15);
    assertThat(saved.getUpdatedAt()).isEqualTo(application.getUpdatedAt());
  }

  @Test
  @DisplayName("Prazo abaixo de um dia responde 400 apontando o campo, sem gravar")
  void recusa_prazo_invalido() {
    client
        .patch()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"retentionDays\":0}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors.retentionDays")
        .exists();

    assertThat(reload().getRetentionDays()).isEqualTo(180);
  }

  @Test
  @DisplayName("Nome como null e JSON malformado respondem 400 sem gravar")
  void recusa_corpo_malformado() {
    var bodies = new String[] {"{\"name\":null}", "{\"retentionDays\":\"trinta\"}", "{\"name\":"};
    for (var body : bodies) {
      client
          .patch()
          .uri(uri())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("request.invalid");
    }

    assertThat(reload().getName()).isEqualTo("Acme App");
  }

  @Test
  @DisplayName("Retenção de texto livre acima da geral responde 422 sem gravar nada do pedido")
  void recusa_texto_livre_acima_da_geral() {
    client
        .patch()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"name\":\"Outro\",\"openTextRetentionDays\":900}")
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.open_text_retention_invalid");

    var saved = reload();
    assertThat(saved.getName()).isEqualTo("Acme App");
    assertThat(saved.getOpenTextRetentionDays()).isEqualTo(30);
  }

  @Test
  @DisplayName("Identificador inexistente e malformado respondem 404")
  void recusa_inexistente_e_malformado() {
    for (var id : new String[] {UUID.randomUUID().toString(), "nao-e-um-id"}) {
      client
          .patch()
          .uri("/applications/" + id)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"name\":\"Outro\"}")
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("application.not_found");
    }
  }

  @Test
  @DisplayName("A chave do SDK não vale no painel: 403")
  void recusa_chave_de_aplicacao() {
    client
        .patch()
        .uri(uri())
        .header("X-Pitaco-Key", "pit_qualquer")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"name\":\"Outro\"}")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");

    assertThat(reload().getName()).isEqualTo("Acme App");
  }
}
