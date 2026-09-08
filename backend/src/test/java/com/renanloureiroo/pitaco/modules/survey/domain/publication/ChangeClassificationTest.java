package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification.DifferenceKind;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleRange;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Classificação da mudança entre versões")
class ChangeClassificationTest {

  private SurveyVersion previous;
  private SurveyVersion current;

  @BeforeEach
  void setUp() {
    previous = SurveyVersion.create(SurveyId.generate(), 1);
    previous.addQuestion(freeText("Primeira"));
    previous.addQuestion(choice("Segunda", "yes", "no"));

    current = previous.copyAsDraft(2);
  }

  private static Question.Draft freeText(String statement) {
    return new Question.Draft(
        QuestionStatement.of(statement), QuestionType.FREE_TEXT, true, List.of(), Optional.empty());
  }

  private static Question.Draft choice(String statement, String... values) {
    var options = new java.util.ArrayList<QuestionOption>();
    for (var index = 0; index < values.length; index++) {
      options.add(new QuestionOption("Rótulo " + values[index], values[index], index + 1));
    }
    return new Question.Draft(
        QuestionStatement.of(statement),
        QuestionType.SINGLE_CHOICE,
        true,
        List.copyOf(options),
        Optional.empty());
  }

  private List<ChangeClassification.Difference> differences() {
    return ChangeClassification.between(previous.getQuestions(), current.getQuestions());
  }

  @Test
  @DisplayName("Sem mudança estrutural, não há diferença nenhuma")
  void copia_intacta_nao_tem_diferenca() {
    assertThat(differences()).isEmpty();
  }

  @Test
  @DisplayName("Pergunta acrescentada derruba a declaração cosmética")
  void pergunta_acrescentada() {
    var nova = current.addQuestion(freeText("Terceira"));

    assertThat(differences())
        .singleElement()
        .satisfies(
            difference -> {
              assertThat(difference.kind()).isEqualTo(DifferenceKind.QUESTION_ADDED);
              assertThat(difference.questionKey()).isEqualTo(nova.getKey());
            });
  }

  @Test
  @DisplayName("Pergunta removida derruba a declaração cosmética")
  void pergunta_removida() {
    var removida = current.getQuestions().getFirst();
    current.removeQuestion(removida.id());

    assertThat(differences())
        .singleElement()
        .satisfies(
            difference -> {
              assertThat(difference.kind()).isEqualTo(DifferenceKind.QUESTION_REMOVED);
              assertThat(difference.questionKey()).isEqualTo(removida.getKey());
            });
  }

  @Test
  @DisplayName("Pergunta retipada derruba a declaração cosmética")
  void pergunta_retipada() {
    var alvo = current.getQuestions().getFirst();
    current.updateQuestion(
        alvo.id(),
        new Question.Draft(
            alvo.getStatement(),
            QuestionType.SCALE,
            true,
            List.of(),
            Optional.of(new ScaleRange(1, 5))));

    assertThat(differences())
        .singleElement()
        .satisfies(
            difference -> {
              assertThat(difference.kind()).isEqualTo(DifferenceKind.TYPE_CHANGED);
              assertThat(difference.questionKey()).isEqualTo(alvo.getKey());
            });
  }

  @Test
  @DisplayName("Conjunto de opções alterado derruba a declaração cosmética")
  void opcoes_alteradas() {
    var alvo = current.getQuestions().get(1);
    current.updateQuestion(alvo.id(), choice(alvo.getStatement().value(), "yes", "no", "maybe"));

    assertThat(differences())
        .singleElement()
        .satisfies(
            difference -> {
              assertThat(difference.kind()).isEqualTo(DifferenceKind.OPTIONS_CHANGED);
              assertThat(difference.questionKey()).isEqualTo(alvo.getKey());
            });
  }

  @Test
  @DisplayName("Reordenar as alternativas não muda o que se pode responder")
  void ordem_das_opcoes_nao_conta() {
    var alvo = current.getQuestions().get(1);
    current.updateQuestion(alvo.id(), choice(alvo.getStatement().value(), "no", "yes"));

    assertThat(differences()).isEmpty();
  }

  @Test
  @DisplayName("Enunciado reescrito não derruba a declaração cosmética")
  void enunciado_reescrito_nao_conta() {
    var alvo = current.getQuestions().getFirst();
    current.updateQuestion(alvo.id(), freeText("Primeira, com outras palavras"));

    assertThat(differences()).isEmpty();
  }

  @Test
  @DisplayName("Obrigatoriedade trocada não derruba a declaração cosmética")
  void obrigatoriedade_nao_conta() {
    var alvo = current.getQuestions().getFirst();
    current.updateQuestion(
        alvo.id(),
        new Question.Draft(
            alvo.getStatement(), QuestionType.FREE_TEXT, false, List.of(), Optional.empty()));

    assertThat(differences()).isEmpty();
  }

  @Test
  @DisplayName("Reordenar as perguntas não derruba a declaração cosmética: compara-se por chave")
  void reordenacao_nao_conta() {
    var ids = current.getQuestions().stream().map(Question::id).toList();
    current.reorder(List.of(ids.get(1), ids.get(0)));

    assertThat(differences()).isEmpty();
  }

  @Test
  @DisplayName("Acrescentar e remover ao mesmo tempo rende as duas diferenças")
  void acumula_as_diferencas() {
    var removida = current.getQuestions().getFirst();
    current.removeQuestion(removida.id());
    current.addQuestion(freeText("Terceira"));

    assertThat(differences())
        .extracting(ChangeClassification.Difference::kind)
        .containsExactlyInAnyOrder(DifferenceKind.QUESTION_ADDED, DifferenceKind.QUESTION_REMOVED);
  }
}
