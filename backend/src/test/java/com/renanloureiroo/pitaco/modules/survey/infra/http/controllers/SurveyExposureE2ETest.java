package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("PATCH /applications/{applicationId}/surveys/{surveyId} — exposição")
class SurveyExposureE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;
  private String surveyId;

  private record Stored(int priority, Integer responseQuota, boolean ignoresQuietPeriod) {}

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));

    surveyId =
        client
            .post()
            .uri(base())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateSurveyRequestDTO("NPS pós-checkout"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(SurveyResponseDTO.class)
            .returnResult()
            .getResponseBody()
            .id();
  }

  private String base() {
    return "/applications/" + application.getId() + "/surveys";
  }

  private RestTestClient.ResponseSpec patch(String body) {
    return client
        .patch()
        .uri(base() + "/" + surveyId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private SurveyResponseDTO patchOk(String body) {
    return patch(body)
        .expectStatus()
        .isOk()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private Stored stored() {
    return jdbc.sql(
            "select priority, response_quota, ignores_quiet_period from surveys where id = :id")
        .param("id", surveyId)
        .query(
            (rs, row) ->
                new Stored(
                    rs.getInt("priority"),
                    (Integer) rs.getObject("response_quota"),
                    rs.getBoolean("ignores_quiet_period")))
        .single();
  }

  @Test
  @DisplayName("Define prioridade, cota e isenção, e o banco e a consulta devolvem o mesmo")
  void define_a_exposicao() {
    var body = patchOk("{\"priority\":10,\"responseQuota\":100,\"ignoresQuietPeriod\":true}");

    assertThat(body.priority()).isEqualTo(10);
    assertThat(body.responseQuota()).isEqualTo(100);
    assertThat(body.ignoresQuietPeriod()).isTrue();
    assertThat(body.state()).isEqualTo("draft");
    assertThat(stored()).isEqualTo(new Stored(10, 100, true));

    var detail =
        client
            .get()
            .uri(base() + "/" + surveyId)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyDetailResponseDTO.class)
            .returnResult()
            .getResponseBody();
    assertThat(detail.priority()).isEqualTo(10);
    assertThat(detail.responseQuota()).isEqualTo(100);
    assertThat(detail.ignoresQuietPeriod()).isTrue();
  }

  @Test
  @DisplayName("Nasce com prioridade zero, sem cota e respeitando o descanso")
  void nasce_com_a_exposicao_padrao() {
    assertThat(stored()).isEqualTo(new Stored(0, null, false));
  }

  @Test
  @DisplayName("Cota enviada como null é removida; os demais campos ficam como estavam")
  void cota_nula_remove() {
    patchOk("{\"priority\":5,\"responseQuota\":20}");

    var body = patchOk("{\"responseQuota\":null}");

    assertThat(body.responseQuota()).isNull();
    assertThat(stored()).isEqualTo(new Stored(5, null, false));
  }

  @Test
  @DisplayName("Corpo vazio devolve 200 e não muda nada")
  void corpo_vazio_nao_muda_nada() {
    patchOk("{\"priority\":-3,\"ignoresQuietPeriod\":true}");

    patchOk("{}");

    assertThat(stored()).isEqualTo(new Stored(-3, null, true));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"priority\":101}",
        "{\"priority\":-101}",
        "{\"responseQuota\":0}",
        "{\"priority\":null}",
        "{\"ignoresQuietPeriod\":null}",
        "{\"ignoresQuietPeriod\":\"sim\"}",
        "{\"name\":\"   \"}",
        "{\"name\":null}",
        "{\"priority\": ",
        "[]"
      })
  @DisplayName("Valor fora da regra ou corpo malformado devolve 400 e nada muda")
  void recusa_e_preserva(String body) {
    patch(body).expectStatus().isBadRequest();

    assertThat(stored()).isEqualTo(new Stored(0, null, false));
  }

  @Test
  @DisplayName("Pesquisa desconhecida devolve 404")
  void pesquisa_desconhecida() {
    client
        .patch()
        .uri(base() + "/" + UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"priority\":1}")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");
  }
}
