package com.renanloureiroo.pitaco.modules.results.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.results.application.outputs.QuestionResultOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregate;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryResultsSurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.readmodels.InMemorySurveyResultsReadModel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyResultsUseCase — autoria avançada")
class GetSurveyResultsAdvancedAuthoringTest {

  private static final Instant OPENED = Instant.parse("2026-09-01T10:00:00Z");

  private InMemoryResultsSurveyScopeGateway surveys;
  private InMemorySurveyResultsReadModel results;
  private GetSurveyResultsUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemoryResultsSurveyScopeGateway();
    results = new InMemorySurveyResultsReadModel();
    useCase = new GetSurveyResultsUseCase(surveys, results, Duration.ofMinutes(30), 3);
    applicationId = ApplicationId.generate();
  }

  private static List<QuestionOption> options(String... values) {
    var built = new java.util.ArrayList<QuestionOption>();
    for (var index = 0; index < values.length; index++) {
      built.add(new QuestionOption(values[index], values[index], index + 1));
    }
    return List.copyOf(built);
  }

  private static QuestionDefinition choice(QuestionKey key, String... values) {
    return new QuestionDefinition(
        key, "Recomendaria?", QuestionType.SINGLE_CHOICE, 1, options(values), Optional.empty());
  }

  private static ResultsSelection version(int number) {
    return new ResultsSelection(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(number));
  }

  private QuestionResultOutput only(SurveyId surveyId, ResultsSelection selection) {
    return useCase
        .execute(new GetSurveyResultsUseCase.Input(applicationId.value(), surveyId.value(), selection))
        .questions()
        .getFirst();
  }

  @Test
  @DisplayName("Não aplicável é contado à parte e fica fora do denominador da proporção")
  void conta_nao_aplicaveis() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(surveyId, 1, choice(key, "yes", "no"));
    results.withOptions(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), key, "yes");
    results.withNotApplicable(
        results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED.plusSeconds(1), Map.of()), key);

    var question = only(surveyId, ResultsSelection.none());

    assertThat(question.answered()).isEqualTo(1);
    assertThat(question.skipped()).isZero();
    assertThat(question.notApplicable()).isEqualTo(1);
    var choice = (QuestionAggregate.Choice) question.aggregate().orElseThrow();
    assertThat(choice.options().getFirst().share()).isCloseTo(1.0, within(0.0001));
  }

  @Test
  @DisplayName("No consolidado, opção trocada entre versões exibidas marca a pergunta como incomparável")
  void consolidado_marca_incomparavel() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(surveyId, 1, choice(key, "yes", "no"));
    results.withQuestion(surveyId, 2, choice(key, "yes", "maybe"));
    results.aDisplayInVersion(applicationId, surveyId, 1, "COMPLETED", OPENED);
    results.aDisplayInVersion(applicationId, surveyId, 2, "COMPLETED", OPENED);

    var comparability = only(surveyId, ResultsSelection.none()).comparability().orElseThrow();

    assertThat(comparability.comparable()).isFalse();
    assertThat(comparability.versions()).containsExactly(1, 2);
  }

  @Test
  @DisplayName("Pergunta que não mudou entre as versões é segura de somar")
  void consolidado_seguro_quando_nao_mudou() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(surveyId, 1, choice(key, "yes", "no"));
    results.withQuestion(surveyId, 2, choice(key, "no", "yes"));
    results.aDisplayInVersion(applicationId, surveyId, 1, "COMPLETED", OPENED);
    results.aDisplayInVersion(applicationId, surveyId, 2, "COMPLETED", OPENED);

    var comparability = only(surveyId, ResultsSelection.none()).comparability().orElseThrow();

    assertThat(comparability.comparable()).isTrue();
  }

  @Test
  void versao_sem_exibicao_nao_estraga_a_soma() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(surveyId, 1, choice(key, "yes", "no"));
    results.withQuestion(surveyId, 2, choice(key, "yes", "maybe"));
    results.aDisplayInVersion(applicationId, surveyId, 1, "COMPLETED", OPENED);

    var comparability = only(surveyId, ResultsSelection.none()).comparability().orElseThrow();

    assertThat(comparability.comparable()).isTrue();
    assertThat(comparability.versions()).containsExactly(1);
  }

  @Test
  @DisplayName("Lida uma versão por vez, não há soma a desconfiar: a marca não vem")
  void por_versao_sem_comparabilidade() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(surveyId, 1, choice(key, "yes", "no"));
    results.withQuestion(surveyId, 2, choice(key, "yes", "maybe"));
    results.aDisplayInVersion(applicationId, surveyId, 1, "COMPLETED", OPENED);
    results.aDisplayInVersion(applicationId, surveyId, 2, "COMPLETED", OPENED);

    assertThat(only(surveyId, version(1)).comparability()).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa do modelo NPS traz o NPS no topo, calculado da pergunta do modelo")
  void nps_no_topo() {
    var surveyId = surveys.aPublishedNpsSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(
        surveyId,
        1,
        new QuestionDefinition(key, "NPS", QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS)));
    results.withNumber(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), key, 10);
    results.withNumber(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), key, 9);
    results.withNumber(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), key, 2);

    var nps =
        useCase
            .execute(
                new GetSurveyResultsUseCase.Input(
                    applicationId.value(), surveyId.value(), ResultsSelection.none()))
            .nps()
            .orElseThrow();

    assertThat(nps.questionKey()).isEqualTo(key);
    assertThat(nps.respondents()).isEqualTo(3);
    assertThat(nps.promoters()).isEqualTo(2);
    assertThat(nps.detractors()).isEqualTo(1);
    assertThat(nps.score()).hasValueSatisfying(score -> assertThat(score).isCloseTo(33.33, within(0.01)));
  }

  @Test
  void nps_do_modelo_sem_resposta_tem_score_ausente() {
    var surveyId = surveys.aPublishedNpsSurveyIn(applicationId);
    results.withQuestion(
        surveyId,
        1,
        new QuestionDefinition(
            QuestionKey.generate(), "NPS", QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS)));

    var nps =
        useCase
            .execute(
                new GetSurveyResultsUseCase.Input(
                    applicationId.value(), surveyId.value(), ResultsSelection.none()))
            .nps()
            .orElseThrow();

    assertThat(nps.respondents()).isZero();
    assertThat(nps.score()).isEmpty();
  }

  @Test
  void sem_modelo_nao_ha_nps_no_topo() {
    var surveyId = surveys.aPublishedSurveyIn(applicationId);
    var key = QuestionKey.generate();
    results.withQuestion(
        surveyId,
        1,
        new QuestionDefinition(key, "NPS", QuestionType.NPS, 1, List.of(), Optional.of(ScaleRange.NPS)));
    results.withNumber(results.aDisplay(applicationId, surveyId, "COMPLETED", OPENED, Map.of()), key, 10);

    var output =
        useCase.execute(
            new GetSurveyResultsUseCase.Input(
                applicationId.value(), surveyId.value(), ResultsSelection.none()));

    assertThat(output.nps()).isEmpty();
  }
}
