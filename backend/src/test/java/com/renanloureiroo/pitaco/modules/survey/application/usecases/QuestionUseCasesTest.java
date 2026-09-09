package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.QuestionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyContentFrozen;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.services.QuestionDrafts;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionId;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Casos de uso de pergunta")
class QuestionUseCasesTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private ApplicationId applicationId;
  private Survey survey;
  private SurveyVersion draft;

  private AddQuestionUseCase add;
  private UpdateQuestionUseCase update;
  private RemoveQuestionUseCase remove;
  private ReorderQuestionsUseCase reorder;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    applicationId = ApplicationId.generate();
    survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    draft = SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).buildSavedIn(versions);

    add = new AddQuestionUseCase(surveys, versions);
    update = new UpdateQuestionUseCase(surveys, versions);
    remove = new RemoveQuestionUseCase(surveys, versions);
    reorder = new ReorderQuestionsUseCase(surveys, versions);
  }

  private QuestionDrafts.Draft freeText(String statement) {
    return QuestionFactory.aFreeTextQuestion().withStatement(statement).asInputDraft();
  }

  private QuestionOutput addQuestion(String statement) {
    return add.execute(
        new AddQuestionUseCase.Input(
            applicationId.value(), survey.id().value(), freeText(statement)));
  }

  private SurveyVersion stored() {
    return versions.findDraft(survey.id()).orElseThrow();
  }

  private void publishTheDraft() {
    versions.delete(draft.id());
    SurveyVersionFactory.aVersion().forSurvey(survey.id()).buildPublishedSavedIn(versions);
  }

  @Nested
  @DisplayName("AddQuestionUseCase")
  class Add {

    @Test
    @DisplayName("Resolve a versão editável e acrescenta na última posição")
    void acrescenta_na_ultima_posicao() {
      var first = addQuestion("Primeira");
      var second = addQuestion("Segunda");

      assertThat(first.position()).isEqualTo(1);
      assertThat(second.position()).isEqualTo(2);
      assertThat(first.key()).isNotEqualTo(second.key());
      assertThat(stored().getQuestions()).extracting(Question::getPosition).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Escolha sem nenhuma opção é aceita no rascunho")
    void escolha_sem_opcao_e_aceita() {
      var output =
          add.execute(
              new AddQuestionUseCase.Input(
                  applicationId.value(),
                  survey.id().value(),
                  QuestionFactory.anIncompleteChoiceQuestion().asInputDraft()));

      assertThat(output.type()).isEqualTo(QuestionType.SINGLE_CHOICE);
      assertThat(output.options()).isEmpty();
    }

    @Test
    @DisplayName("Sem rascunho aberto, o conteúdo está congelado")
    void recusa_sem_rascunho() {
      publishTheDraft();

      var input =
          new AddQuestionUseCase.Input(
              applicationId.value(), survey.id().value(), freeText("Nova"));

      assertThatThrownBy(() -> add.execute(input))
          .isInstanceOf(SurveyContentFrozen.class)
          .satisfies(
              erro ->
                  assertThat(((SurveyContentFrozen) erro).code())
                      .isEqualTo("survey.content_frozen"));
    }

    @Test
    void recusa_fora_do_escopo_da_aplicacao() {
      var input =
          new AddQuestionUseCase.Input(
              ApplicationId.generate().value(), survey.id().value(), freeText("Nova"));

      assertThatThrownBy(() -> add.execute(input)).isInstanceOf(SurveyNotFound.class);
    }
  }

  @Nested
  @DisplayName("UpdateQuestionUseCase")
  class Update {

    @Test
    @DisplayName("Reescreve o conteúdo mantendo a chave estável")
    void reescreve_mantendo_a_chave() {
      var question = addQuestion("Primeira");

      var output =
          update.execute(
              new UpdateQuestionUseCase.Input(
                  applicationId.value(),
                  survey.id().value(),
                  question.id(),
                  QuestionFactory.aSingleChoiceQuestion()
                      .withStatement("Primeira, corrigida")
                      .asInputDraft()));

      assertThat(output.key()).isEqualTo(question.key());
      assertThat(output.id()).isEqualTo(question.id());
      assertThat(output.statement()).isEqualTo("Primeira, corrigida");
      assertThat(output.type()).isEqualTo(QuestionType.SINGLE_CHOICE);
      assertThat(output.options()).hasSize(2);
      assertThat(output.position()).isEqualTo(1);
    }

    @Test
    @DisplayName("Pergunta de outra versão, inexistente ou malformada recusam do mesmo jeito")
    void recusa_pergunta_desconhecida() {
      addQuestion("Primeira");

      assertThatThrownBy(
              () ->
                  update.execute(
                      new UpdateQuestionUseCase.Input(
                          applicationId.value(),
                          survey.id().value(),
                          QuestionId.generate().value(),
                          freeText("Outra"))))
          .isInstanceOf(QuestionNotFound.class);

      assertThatThrownBy(
              () ->
                  update.execute(
                      new UpdateQuestionUseCase.Input(
                          applicationId.value(),
                          survey.id().value(),
                          "nao-e-um-id",
                          freeText("Outra"))))
          .isInstanceOf(QuestionNotFound.class);
    }

    @Test
    void recusa_sem_rascunho() {
      var question = addQuestion("Primeira");
      publishTheDraft();

      var input =
          new UpdateQuestionUseCase.Input(
              applicationId.value(), survey.id().value(), question.id(), freeText("Outra"));

      assertThatThrownBy(() -> update.execute(input)).isInstanceOf(SurveyContentFrozen.class);
    }
  }

  @Nested
  @DisplayName("RemoveQuestionUseCase")
  class Remove {

    @Test
    @DisplayName("Remover a do meio deixa as demais consecutivas")
    void recompacta_as_posicoes() {
      var first = addQuestion("Primeira");
      var second = addQuestion("Segunda");
      var third = addQuestion("Terceira");

      remove.execute(
          new RemoveQuestionUseCase.Input(applicationId.value(), survey.id().value(), second.id()));

      assertThat(stored().getQuestions()).extracting(Question::getPosition).containsExactly(1, 2);
      assertThat(stored().getQuestions())
          .extracting(question -> question.id().value())
          .containsExactly(first.id(), third.id());
    }

    @Test
    void recusa_pergunta_desconhecida() {
      var input =
          new RemoveQuestionUseCase.Input(
              applicationId.value(), survey.id().value(), QuestionId.generate().value());

      assertThatThrownBy(() -> remove.execute(input)).isInstanceOf(QuestionNotFound.class);
    }

    @Test
    void recusa_sem_rascunho() {
      var question = addQuestion("Primeira");
      publishTheDraft();

      var input =
          new RemoveQuestionUseCase.Input(
              applicationId.value(), survey.id().value(), question.id());

      assertThatThrownBy(() -> remove.execute(input)).isInstanceOf(SurveyContentFrozen.class);
    }
  }

  @Nested
  @DisplayName("ReorderQuestionsUseCase")
  class Reorder {

    @Test
    @DisplayName("Devolve a nova ordem, sem buraco nem repetição")
    void reordena() {
      var first = addQuestion("Primeira");
      var second = addQuestion("Segunda");
      var third = addQuestion("Terceira");

      var output =
          reorder.execute(
              new ReorderQuestionsUseCase.Input(
                  applicationId.value(),
                  survey.id().value(),
                  List.of(third.id(), first.id(), second.id())));

      assertThat(output)
          .extracting(QuestionOutput::statement)
          .containsExactly("Terceira", "Primeira", "Segunda");
      assertThat(output).extracting(QuestionOutput::position).containsExactly(1, 2, 3);
      assertThat(stored().getQuestions())
          .extracting(Question::getPosition)
          .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Permutação inexata é recusada e a ordem anterior fica intacta")
    void recusa_permutacao_inexata() {
      var first = addQuestion("Primeira");
      addQuestion("Segunda");

      assertThatThrownBy(
              () ->
                  reorder.execute(
                      new ReorderQuestionsUseCase.Input(
                          applicationId.value(), survey.id().value(), List.of(first.id()))))
          .satisfies(
              erro ->
                  assertThat(erro)
                      .isInstanceOf(com.renanloureiroo.pitaco.core.error.DomainException.class));

      assertThatThrownBy(
              () ->
                  reorder.execute(
                      new ReorderQuestionsUseCase.Input(
                          applicationId.value(),
                          survey.id().value(),
                          List.of(first.id(), "nao-e-um-id"))))
          .satisfies(
              erro ->
                  assertThat(((com.renanloureiroo.pitaco.core.error.DomainException) erro).code())
                      .isEqualTo("question.order_invalid"));

      assertThat(stored().getQuestions())
          .extracting(question -> question.getStatement().value())
          .containsExactly("Primeira", "Segunda");
    }

    @Test
    void recusa_sem_rascunho() {
      var question = addQuestion("Primeira");
      publishTheDraft();

      var input =
          new ReorderQuestionsUseCase.Input(
              applicationId.value(), survey.id().value(), List.of(question.id()));

      assertThatThrownBy(() -> reorder.execute(input)).isInstanceOf(SurveyContentFrozen.class);
    }
  }

  @Test
  @DisplayName("O rascunho da factory não traz faixa nem opções por engano")
  void a_factory_de_texto_livre_nasce_limpa() {
    assertThat(freeText("x").options()).isEmpty();
    assertThat(freeText("x").range()).isEqualTo(Optional.empty());
  }
}
