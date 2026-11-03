package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Impedimentos de publicação")
class PublicationImpedimentsTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private SurveyVersion version;

  @BeforeEach
  void setUp() {
    version = SurveyVersion.create(SurveyId.generate(), 1);
  }

  private void defineTrigger() {
    version.defineTrigger(
        new Trigger(
            EventName.of("checkout.completed"),
            TriggerWindow.of(NOW, null),
            SamplingRate.of(0.25)));
  }

  private Question addFreeText(String statement) {
    return version.addQuestion(
        new Question.Draft(
            QuestionStatement.of(statement),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty()));
  }

  private Question addChoiceWithoutOptions(String statement) {
    return version.addQuestion(
        new Question.Draft(
            QuestionStatement.of(statement),
            QuestionType.SINGLE_CHOICE,
            true,
            List.of(),
            Optional.empty()));
  }

  @Test
  @DisplayName("Rascunho vazio acusa a falta de pergunta e a falta de disparo, de uma vez")
  void rascunho_vazio_acusa_tudo() {
    var impediments = version.publicationImpediments();

    assertThat(impediments)
        .extracting(PublicationImpediment::code)
        .containsExactlyInAnyOrder("survey.no_questions", "trigger.missing");
    assertThat(impediments)
        .extracting(PublicationImpediment::field)
        .containsExactlyInAnyOrder("questions", "trigger");
  }

  @Test
  @DisplayName("Escolha sem opção vira impedimento, apontando a chave da pergunta")
  void escolha_sem_opcao_e_impedimento() {
    defineTrigger();
    var question = addChoiceWithoutOptions("Como avalia o suporte?");

    var impediments = version.publicationImpediments();

    assertThat(impediments)
        .singleElement()
        .satisfies(
            impediment -> {
              assertThat(impediment.code()).isEqualTo("question.options_missing");
              assertThat(impediment.field()).isEqualTo("questions[0].options");
              assertThat(impediment.questionKey()).contains(question.getKey());
            });
  }

  @Test
  @DisplayName("A lista é sempre completa: duas perguntas sem opção rendem dois impedimentos")
  void relata_todos_os_impedimentos_de_uma_vez() {
    var first = addChoiceWithoutOptions("Primeira");
    var second = addChoiceWithoutOptions("Segunda");

    var impediments = version.publicationImpediments();

    assertThat(impediments)
        .extracting(PublicationImpediment::code)
        .containsExactlyInAnyOrder(
            "question.options_missing", "question.options_missing", "trigger.missing");
    assertThat(impediments)
        .filteredOn(impediment -> impediment.questionKey().isPresent())
        .extracting(impediment -> impediment.questionKey().orElseThrow())
        .containsExactlyInAnyOrder(first.getKey(), second.getKey());
    assertThat(impediments)
        .filteredOn(impediment -> impediment.code().equals("question.options_missing"))
        .extracting(PublicationImpediment::field)
        .containsExactlyInAnyOrder("questions[0].options", "questions[1].options");
  }

  @Test
  @DisplayName("Rascunho completo não tem nenhum impedimento")
  void rascunho_completo_nao_tem_impedimento() {
    defineTrigger();
    addFreeText("O que achou?");
    version.addQuestion(
        new Question.Draft(
            QuestionStatement.of("Recomendaria?"),
            QuestionType.SINGLE_CHOICE,
            true,
            List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2)),
            Optional.empty()));
    version.addQuestion(
        new Question.Draft(
            QuestionStatement.of("De 0 a 10"),
            QuestionType.NPS,
            true,
            List.of(),
            Optional.of(ScaleRange.NPS)));

    assertThat(version.publicationImpediments()).isEmpty();
  }

  @Test
  @DisplayName("Tipo que não exige opção nunca acusa opção faltando")
  void tipo_sem_opcao_nao_acusa() {
    defineTrigger();
    addFreeText("Comentário livre");

    assertThat(version.publicationImpediments()).isEmpty();
  }

  @Test
  @DisplayName("Enunciado vazio é invariante de construção, então nunca sobrevive até aqui")
  void enunciado_vazio_nao_chega_a_ser_impedimento() {
    defineTrigger();
    addFreeText("   Enunciado com espaço   ");

    assertThat(version.publicationImpediments())
        .extracting(PublicationImpediment::code)
        .doesNotContain("question.statement_missing");
  }

  @Test
  @DisplayName("Consultar impedimentos não altera nada da versão")
  void consultar_nao_escreve() {
    addChoiceWithoutOptions("Primeira");
    var antes = version.getQuestions();

    version.publicationImpediments();
    version.publicationImpediments();

    assertThat(version.getQuestions()).isEqualTo(antes);
    assertThat(version.publishedAt()).isEmpty();
    assertThat(version.isEditable()).isTrue();
  }
}
