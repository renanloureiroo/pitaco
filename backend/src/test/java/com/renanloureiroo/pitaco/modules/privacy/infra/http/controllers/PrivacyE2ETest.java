package com.renanloureiroo.pitaco.modules.privacy.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RespondentErasureResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RetentionPreviewResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Privacidade: exclusão de respondente, registros e prévia da retenção")
class PrivacyE2ETest extends PrivacyE2ESupport {

  private static final Instant ANSWERED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Autowired RestTestClient client;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private SeededSurvey first;
  private SeededSurvey second;

  @BeforeEach
  void setUp() {
    database.clean();
    applicationId = seedApplication();
    first = seedSurvey(applicationId);
    second = seedSurvey(applicationId);

    var excluded = respondentByReference(applicationId, "u-excluir");
    answered(applicationId, first, excluded, ANSWERED_AT, 2, "Achei confuso");
    answered(applicationId, second, excluded, ANSWERED_AT, 3, "Meu telefone é 9999");

    var kept = respondentByReference(applicationId, "u-fica");
    answered(applicationId, first, kept, ANSWERED_AT.plusSeconds(60), 10, "Ótimo");
  }

  private RestTestClient.ResponseSpec erase(String query) {
    return client.delete().uri(base(applicationId) + "/respondents" + query).exchange();
  }

  private RespondentErasureResponseDTO eraseOk(String query) {
    return erase(query)
        .expectStatus()
        .isOk()
        .expectBody(RespondentErasureResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private SurveyResultsResponseDTO results(SeededSurvey survey) {
    return client
        .get()
        .uri(base(applicationId) + "/surveys/" + survey.surveyId().value() + "/results")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyResultsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private String csv(SeededSurvey survey) {
    return client
        .get()
        .uri(base(applicationId) + "/surveys/" + survey.surveyId().value() + "/results/export")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Apaga tudo do respondente nas duas pesquisas, e resultados e export param de contar")
  void apaga_tudo_do_respondente() {
    var body = eraseOk("?reference=u-excluir");

    assertThat(body.deleted()).isTrue();
    assertThat(body.displaysDeleted()).isEqualTo(2);
    assertThat(body.answersDeleted()).isEqualTo(4);

    assertThat(count("select count(*) from respondents where identity_value = ?", "u-excluir")).isZero();
    assertThat(count("select count(*) from survey_displays")).isEqualTo(1);
    assertThat(count("select count(*) from survey_answers")).isEqualTo(2);
    assertThat(count("select count(*) from survey_display_attributes")).isEqualTo(1);
    assertThat(count("select count(*) from respondents where identity_value = ?", "u-fica")).isOne();

    assertThat(results(first).responseRate().displayed()).isEqualTo(1);
    assertThat(results(second).responseRate().displayed()).isZero();
    assertThat(csv(first)).doesNotContain("u-excluir").contains("u-fica");
    assertThat(csv(second)).doesNotContain("u-excluir").doesNotContain("9999");
  }

  @Test
  @DisplayName("O registro guarda quando e quanto saiu, nunca a referência excluída")
  void registro_sem_referencia() {
    eraseOk("?reference=u-excluir");

    var rows = jdbc.sql("select * from deletion_audits").query().listOfRows();
    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.get("application_id")).isEqualTo(applicationId.value());
      assertThat(row.get("displays_deleted")).isEqualTo(2);
      assertThat(row.get("answers_deleted")).isEqualTo(4);
      assertThat(row.get("performed_by")).isNull();
      assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains("u-excluir"));
    });

    client
        .get()
        .uri(base(applicationId) + "/deletion-audits")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total").isEqualTo(1)
        .jsonPath("$.items[0].displaysDeleted").isEqualTo(2)
        .jsonPath("$.items[0].answersDeleted").isEqualTo(4)
        .jsonPath("$.items[0].performedBy").doesNotExist();
  }

  @Test
  @DisplayName("Pedido repetido responde deleted=false, não apaga nada e não cria registro")
  void pedido_repetido() {
    eraseOk("?reference=u-excluir");

    var again = eraseOk("?reference=u-excluir");

    assertThat(again.deleted()).isFalse();
    assertThat(again.displaysDeleted()).isZero();
    assertThat(count("select count(*) from deletion_audits")).isOne();
    assertThat(count("select count(*) from survey_displays")).isEqualTo(1);
  }

  @Test
  @DisplayName("Respondente sem referência é excluído pelo dispositivo")
  void pelo_dispositivo() {
    var device = respondentByDevice(applicationId, "device-1");
    answered(applicationId, first, device, ANSWERED_AT, 7, "ok");

    var body = eraseOk("?deviceId=device-1");

    assertThat(body.deleted()).isTrue();
    assertThat(count("select count(*) from respondents where identity_value = ?", "device-1")).isZero();
  }

  @Test
  @DisplayName("Sem identidade ou com as duas é 400, e nada é apagado nem registrado")
  void validacao() {
    erase("").expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("request.invalid");
    erase("?reference=u-excluir&deviceId=device-1").expectStatus().isBadRequest();
    erase("?reference=" + "a".repeat(201)).expectStatus().isBadRequest();

    assertThat(count("select count(*) from survey_displays")).isEqualTo(3);
    assertThat(count("select count(*) from deletion_audits")).isZero();
  }

  @Test
  @DisplayName("Chave do SDK é 403 e aplicação inexistente é 404, nas três rotas")
  void recusas() {
    client.delete().uri(base(applicationId) + "/respondents?reference=u-excluir")
        .header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client.get().uri(base(applicationId) + "/deletion-audits")
        .header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client.get().uri(base(applicationId) + "/retention-preview")
        .header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();

    var unknown = "/applications/" + UUID.randomUUID();
    client.delete().uri(unknown + "/respondents?reference=u-excluir").exchange()
        .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("application.not_found");
    client.get().uri(unknown + "/deletion-audits").exchange().expectStatus().isNotFound();
    client.get().uri(unknown + "/retention-preview").exchange().expectStatus().isNotFound();
    client.get().uri(base(applicationId) + "/deletion-audits?size=0").exchange()
        .expectStatus().isBadRequest();

    assertThat(count("select count(*) from survey_displays")).isEqualTo(3);
  }

  @Test
  @DisplayName("Sem prazo, a prévia diz que nada é descartado")
  void previa_sem_prazo() {
    var body =
        client.get().uri(base(applicationId) + "/retention-preview").exchange()
            .expectStatus().isOk()
            .expectBody(RetentionPreviewResponseDTO.class).returnResult().getResponseBody();

    assertThat(body.configured()).isFalse();
    assertThat(body.nextRunAt()).isNull();
    assertThat(body.nextRun().answers()).isZero();
  }

  @Test
  @DisplayName("Com prazo, a prévia conta o que sai na próxima execução, antes do primeiro descarte")
  void previa_com_prazo() {
    retain(applicationId, 30, null);
    var kept = respondentByReference(applicationId, "u-antigo");
    answered(applicationId, first, kept, Instant.now().minus(Duration.ofDays(45)), 9, "antigo");

    var body =
        client.get().uri(base(applicationId) + "/retention-preview").exchange()
            .expectStatus().isOk()
            .expectBody(RetentionPreviewResponseDTO.class).returnResult().getResponseBody();

    assertThat(body.configured()).isTrue();
    assertThat(body.answerRetentionDays()).isEqualTo(30);
    assertThat(body.textRetentionDays()).isEqualTo(30);
    assertThat(body.nextRunAt()).isAfter(Instant.now());
    assertThat(body.nextRun().answers()).isGreaterThanOrEqualTo(2);
    assertThat(body.firstDiscardPending()).isTrue();
    assertThat(body.lastRunAt()).isNull();
  }
}
