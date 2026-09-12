package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.QuestionResultDTO;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Resultados — versões, não aplicável e NPS do modelo")
class AdvancedAuthoringResultsE2ETest extends ResultsE2ESupport {

  @Autowired RestTestClient client;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
    seedSurvey(ApplicationFactory.anApplication());
  }

  private SurveyResultsResponseDTO results(String query) {
    return client
        .get()
        .uri(base + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyResultsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private static QuestionResultDTO question(SurveyResultsResponseDTO body, String key) {
    return body.questions().stream()
        .filter(question -> question.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  // A v2 mantém as chaves da v1 e troca a opção "no" por "maybe" na escolha única: é a mudança de
  // sentido que torna aquela pergunta, e só ela, não somável.
  private SurveyVersionId secondVersionWithChoiceChanged() {
    var first = versions.findByNumber(surveyId, 1).orElseThrow();
    var second = first.copyAsDraft(2);
    var changed =
        second.getQuestions().stream()
            .filter(candidate -> candidate.getKey().equals(choice))
            .findFirst()
            .orElseThrow();
    second.updateQuestion(
        changed.id(),
        new Question.Draft(
            changed.getStatement(),
            QuestionType.SINGLE_CHOICE,
            true,
            List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Talvez", "maybe", 2)),
            Optional.empty()));

    var published =
        SurveyVersion.restore(
            second.id(),
            surveyId,
            2,
            SurveyVersionStatus.PUBLISHED,
            second.getQuestions(),
            second.trigger(),
            second.getRules(),
            Optional.empty(),
            Optional.empty(),
            2,
            Optional.of(Instant.now()));
    return versions.create(published).id();
  }

  @Test
  @DisplayName("No consolidado, só a pergunta que mudou entre as versões exibidas vem marcada")
  void consolidado_marca_so_a_pergunta_que_mudou() {
    var second = secondVersionWithChoiceChanged();
    var onFirst = display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of());
    options(onFirst, choice, "no");
    number(onFirst, nps, 9);
    var onSecond = displayOn(second, DisplayOutcome.COMPLETED, DAY_TWO, Map.of());
    options(onSecond, choice, "maybe");
    number(onSecond, nps, 10);

    var body = results("");

    var choiceResult = question(body, choice.value());
    assertThat(choiceResult.comparability().comparable()).isFalse();
    assertThat(choiceResult.comparability().versions()).containsExactly(1, 2);
    assertThat(choiceResult.aggregate().options())
        .extracting(option -> option.value())
        .contains("yes", "maybe", "no");

    var npsResult = question(body, nps.value());
    assertThat(npsResult.comparability().comparable()).isTrue();
    assertThat(npsResult.comparability().versions()).containsExactly(1, 2);

    assertThat(question(results("?version=2"), choice.value()).comparability()).isNull();
  }

  @Test
  @DisplayName("Não aplicável é contado à parte e sai como n/a no CSV")
  void nao_aplicavel_contado_e_exportado() {
    var only = display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of());
    number(only, nps, 9);
    AnswerFactory.anAnswer().forDisplay(only).forQuestion(text).notApplicable().buildSavedIn(answers);

    var textResult = question(results(""), text.value());
    assertThat(textResult.notApplicable()).isEqualTo(1);
    assertThat(textResult.answered()).isZero();
    assertThat(textResult.aggregate()).isNull();

    var csv =
        client
            .get()
            .uri(base + "/export")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertThat(csv).contains(",9,,,n/a\r\n");
  }

  @Test
  @DisplayName("Pesquisa do modelo NPS traz o NPS no topo; sem modelo, não traz")
  void nps_do_modelo_no_topo() {
    number(display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of()), nps, 10);
    number(display(DisplayOutcome.COMPLETED, DAY_TWO, Map.of()), nps, 3);

    assertThat(results("").nps()).isNull();

    jdbc.sql("update surveys set template_kind = 'NPS' where id = :id")
        .param("id", surveyId.value())
        .update();

    var summary = results("").nps();
    assertThat(summary.questionKey()).isEqualTo(nps.value());
    assertThat(summary.respondents()).isEqualTo(2);
    assertThat(summary.promoters()).isEqualTo(1);
    assertThat(summary.detractors()).isEqualTo(1);
    assertThat(summary.score()).isCloseTo(0.0, within(0.0001));
  }
}
