package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.modules.survey.application.services.QuestionDrafts;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class QuestionFactory {

  public static final String DEFAULT_STATEMENT = "O que achou do atendimento?";

  private String statement = DEFAULT_STATEMENT;
  private QuestionType type = QuestionType.FREE_TEXT;
  private boolean required = true;
  private List<QuestionOption> options = List.of();
  private ScaleRange range;

  private QuestionFactory() {}

  public static QuestionFactory aQuestion() {
    return new QuestionFactory();
  }

  public static QuestionFactory aFreeTextQuestion() {
    return aQuestion().ofType(QuestionType.FREE_TEXT);
  }

  public static QuestionFactory aSingleChoiceQuestion() {
    return aQuestion().ofType(QuestionType.SINGLE_CHOICE).withOptions("yes", "no");
  }

  public static QuestionFactory aMultipleChoiceQuestion() {
    return aQuestion().ofType(QuestionType.MULTIPLE_CHOICE).withOptions("email", "phone", "chat");
  }

  public static QuestionFactory aRatingQuestion() {
    return aQuestion().ofType(QuestionType.RATING).withRange(1, 5);
  }

  public static QuestionFactory aScaleQuestion() {
    return aQuestion().ofType(QuestionType.SCALE).withRange(1, 7);
  }

  public static QuestionFactory anNpsQuestion() {
    return aQuestion().ofType(QuestionType.NPS).withRange(0, 10);
  }

  // Escolha sem nenhuma opção: aceita no rascunho, impedimento na publicação.
  public static QuestionFactory anIncompleteChoiceQuestion() {
    return aQuestion().ofType(QuestionType.SINGLE_CHOICE).withoutOptions();
  }

  public QuestionFactory withStatement(String statement) {
    this.statement = statement;
    return this;
  }

  public QuestionFactory ofType(QuestionType type) {
    this.type = type;
    return this;
  }

  public QuestionFactory optional() {
    this.required = false;
    return this;
  }

  public QuestionFactory withOptions(String... values) {
    var built = new ArrayList<QuestionOption>();
    for (var index = 0; index < values.length; index++) {
      built.add(new QuestionOption("Rótulo " + values[index], values[index], index + 1));
    }
    this.options = List.copyOf(built);
    return this;
  }

  public QuestionFactory withoutOptions() {
    this.options = List.of();
    return this;
  }

  public QuestionFactory withRange(int min, int max) {
    this.range = new ScaleRange(min, max);
    return this;
  }

  // O mesmo conteúdo na forma que o caso de uso recebe: dados crus, não value objects.
  public QuestionDrafts.Draft asInputDraft() {
    return new QuestionDrafts.Draft(
        statement,
        type,
        required,
        options.stream()
            .map(option -> new QuestionDrafts.OptionInput(option.label(), option.value()))
            .toList(),
        Optional.ofNullable(range)
            .map(value -> new QuestionDrafts.RangeInput(value.min(), value.max())));
  }

  public Question.Draft asDraft() {
    return new Question.Draft(
        QuestionStatement.of(statement), type, required, options, Optional.ofNullable(range));
  }

  public Question buildAddedTo(SurveyVersion version) {
    return version.addQuestion(asDraft());
  }

  // Um lote com posições consecutivas, para os testes de ordenação e reordenação.
  public List<Question> buildBatchAddedTo(SurveyVersion version, int amount) {
    var created = new ArrayList<Question>();
    for (var index = 1; index <= amount; index++) {
      created.add(withStatement(DEFAULT_STATEMENT + " " + index).buildAddedTo(version));
    }
    return List.copyOf(created);
  }

  public static List<Question> oneOfEachTypeAddedTo(SurveyVersion version) {
    return List.of(
        aFreeTextQuestion().withStatement("Comentário livre").buildAddedTo(version),
        aSingleChoiceQuestion().withStatement("Recomendaria?").buildAddedTo(version),
        aMultipleChoiceQuestion().withStatement("Por onde prefere falar?").buildAddedTo(version),
        aRatingQuestion().withStatement("Avalie o atendimento").buildAddedTo(version),
        aScaleQuestion().withStatement("Quanto concorda?").buildAddedTo(version),
        anNpsQuestion().withStatement("De 0 a 10, recomendaria?").buildAddedTo(version));
  }
}
