package com.renanloureiroo.pitaco.modules.privacy.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ApplyRetentionUseCase;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RetentionPreviewResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Retenção: descarte das respostas vencidas com o agregado preservado")
class RetentionE2ETest extends PrivacyE2ESupport {

  @Autowired RestTestClient client;
  @Autowired DatabaseCleaner database;
  @Autowired ApplyRetentionUseCase retention;

  private ApplicationId applicationId;
  private SeededSurvey survey;
  private DisplayId middle;
  private DisplayId recent;

  private static Instant daysAgo(int days) {
    return Instant.now().minus(Duration.ofDays(days));
  }

  @BeforeEach
  void setUp() {
    database.clean();
    applicationId = seedApplication();
    retain(applicationId, 30, 10);
    survey = seedSurvey(applicationId);

    for (var index = 0; index < 3; index++) {
      answered(
          applicationId,
          survey,
          respondentByReference(applicationId, "u-velho-" + index),
          daysAgo(40).plusSeconds(index),
          10,
          "texto velho " + index);
    }
    middle =
        answered(applicationId, survey, respondentByReference(applicationId, "u-meio"), daysAgo(15), 5, "texto do meio");
    recent =
        answered(applicationId, survey, respondentByReference(applicationId, "u-novo"), daysAgo(1), 9, "texto novo");
  }

  private SurveyResultsResponseDTO results(String query) {
    return client
        .get()
        .uri(base(applicationId) + "/surveys/" + survey.surveyId().value() + "/results" + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyResultsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Apaga as respostas vencidas, apaga o texto vencido e registra a execução")
  void descarta_e_registra() {
    var output = retention.execute();

    assertThat(output.runs()).singleElement().satisfies(run -> {
      assertThat(run.answersDeleted()).isEqualTo(6);
      assertThat(run.textsCleared()).isEqualTo(1);
    });

    assertThat(count("select count(*) from survey_displays")).isEqualTo(5);
    assertThat(count("select count(*) from survey_answers")).isEqualTo(4);
    assertThat(count("select count(*) from survey_answers where display_id = ? and text_value is null and status = 'ANSWERED' and question_key = ?", middle.value(), survey.text().value()))
        .isOne();
    assertThat(count("select count(*) from survey_answers where display_id = ? and text_value is not null", recent.value()))
        .isOne();

    assertThat(count("select count(*) from aggregate_snapshots")).isOne();
    assertThat(count("select responding_displays from aggregate_snapshots")).isEqualTo(3);
    assertThat(count("select count from aggregate_snapshot_counts where dimension = 'NUMBER' and value = '10'"))
        .isEqualTo(3);
    assertThat(count("select count(*) from retention_runs where answers_deleted = 6 and texts_cleared = 1"))
        .isOne();
  }

  @Test
  @DisplayName("O resultado continua mostrando o agregado histórico, somado ao que ficou")
  void resultado_preserva_o_agregado() {
    retention.execute();

    var body = results("");

    var nps = body.questions().stream().filter(q -> q.type().equals("nps")).findFirst().orElseThrow();
    assertThat(nps.answered()).isEqualTo(5);
    assertThat(nps.aggregate().promoters()).isEqualTo(4);
    assertThat(nps.aggregate().detractors()).isEqualTo(1);
    assertThat(body.sampleSize()).isEqualTo(5);
    assertThat(body.retention()).isNotNull();
    assertThat(body.retention().snapshotApplied()).isTrue();
    assertThat(body.retention().note()).isNotBlank();
  }

  @Test
  @DisplayName("Com recorte de período, o congelado não entra e a resposta diz isso")
  void recorte_nao_soma() {
    retention.execute();

    var body = results("?from=" + daysAgo(20));

    var nps = body.questions().stream().filter(q -> q.type().equals("nps")).findFirst().orElseThrow();
    assertThat(nps.answered()).isEqualTo(2);
    assertThat(body.retention().snapshotApplied()).isFalse();
  }

  @Test
  @DisplayName("O texto descartado some das respostas abertas; a resposta recente continua")
  void respostas_abertas() {
    retention.execute();

    client
        .get()
        .uri(base(applicationId) + "/surveys/" + survey.surveyId().value() + "/results/open-answers")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total").isEqualTo(1)
        .jsonPath("$.items[0].text").isEqualTo("texto novo");
  }

  @Test
  @DisplayName("Rodar de novo não descarta nada, não congela de novo e não registra execução vazia")
  void idempotente() {
    retention.execute();

    var again = retention.execute();

    assertThat(again.runs()).isEmpty();
    assertThat(count("select count(*) from aggregate_snapshots")).isOne();
    assertThat(count("select count(*) from retention_runs")).isOne();
    assertThat(results("").questions().stream().filter(q -> q.type().equals("nps")).findFirst().orElseThrow().answered())
        .isEqualTo(5);
  }

  @Test
  @DisplayName("Depois do descarte, a prévia informa quando ele aconteceu")
  void previa_depois_do_descarte() {
    retention.execute();

    var body =
        client.get().uri(base(applicationId) + "/retention-preview").exchange()
            .expectStatus().isOk()
            .expectBody(RetentionPreviewResponseDTO.class).returnResult().getResponseBody();

    assertThat(body.lastRunAt()).isNotNull();
    assertThat(body.firstDiscardPending()).isFalse();
  }

  @Test
  @DisplayName("Aplicação sem prazo não perde nada")
  void sem_prazo_nada_sai() {
    retain(applicationId, null, null);

    assertThat(retention.execute().runs()).isEmpty();
    assertThat(count("select count(*) from survey_answers")).isEqualTo(10);
    assertThat(count("select count(*) from survey_answers where text_value is not null")).isEqualTo(5);
  }
}
