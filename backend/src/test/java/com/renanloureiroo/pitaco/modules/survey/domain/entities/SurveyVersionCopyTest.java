package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.EventName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SamplingRate;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SurveyVersion — cópia para a versão seguinte")
class SurveyVersionCopyTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private SurveyVersion published;

  @BeforeEach
  void setUp() {
    var version = SurveyVersion.create(SurveyId.generate(), 1);
    version.addQuestion(
        new Question.Draft(
            QuestionStatement.of("O que achou?"),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty()));
    version.addQuestion(
        new Question.Draft(
            QuestionStatement.of("Recomendaria?"),
            QuestionType.SINGLE_CHOICE,
            false,
            List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2)),
            Optional.empty()));
    version.defineTrigger(
        new Trigger(
            EventName.of("checkout.completed"),
            TriggerWindow.of(NOW, null),
            SamplingRate.of(0.25)));
    version.addRule(SegmentationRule.create("plan", RuleOperation.EQUALS, Optional.of("pro")));

    published = version.publish(NOW, Optional.empty(), Optional.empty(), Optional.empty());
  }

  @Test
  @DisplayName("A cópia traz perguntas, disparo e regras, em rascunho e com o número novo")
  void copia_o_conteudo_integral() {
    var draft = published.copyAsDraft(2);

    assertThat(draft.getNumber()).isEqualTo(2);
    assertThat(draft.getStatus()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(draft.publishedAt()).isEmpty();
    assertThat(draft.getSurveyId()).isEqualTo(published.getSurveyId());
    assertThat(draft.getQuestions()).hasSize(2);
    assertThat(draft.trigger()).isEqualTo(published.trigger());
    assertThat(draft.getRules()).extracting(SegmentationRule::attribute).containsExactly("plan");
  }

  @Test
  @DisplayName("Cada pergunta copiada preserva a chave estável e ganha identidade nova")
  void preserva_a_chave_e_troca_a_identidade() {
    var draft = published.copyAsDraft(2);

    assertThat(draft.getQuestions())
        .extracting(Question::getKey)
        .containsExactlyElementsOf(
            published.getQuestions().stream().map(Question::getKey).toList());
    assertThat(draft.getQuestions())
        .extracting(question -> question.id().value())
        .doesNotContainAnyElementsOf(
            published.getQuestions().stream().map(question -> question.id().value()).toList());
    assertThat(draft.getQuestions()).extracting(Question::getPosition).containsExactly(1, 2);
  }

  @Test
  @DisplayName("A versão de origem continua intacta depois da cópia e da edição do rascunho")
  void origem_continua_intacta() {
    var draft = published.copyAsDraft(2);
    var enunciadoOriginal = published.getQuestions().getFirst().getStatement();

    draft.updateQuestion(
        draft.getQuestions().getFirst().id(),
        new Question.Draft(
            QuestionStatement.of("Enunciado corrigido"),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty()));

    assertThat(published.getQuestions().getFirst().getStatement()).isEqualTo(enunciadoOriginal);
    assertThat(published.getStatus()).isEqualTo(SurveyVersionStatus.PUBLISHED);
  }

  @Test
  @DisplayName("Pergunta acrescentada no rascunho nasce com chave própria")
  void pergunta_nova_no_rascunho_tem_chave_propria() {
    var draft = published.copyAsDraft(2);

    var nova =
        draft.addQuestion(
            new Question.Draft(
                QuestionStatement.of("Pergunta nova"),
                QuestionType.FREE_TEXT,
                true,
                List.of(),
                Optional.empty()));

    assertThat(published.getQuestions()).extracting(Question::getKey).doesNotContain(nova.getKey());
    assertThat(nova.getPosition()).isEqualTo(3);
  }

  @Test
  @DisplayName("Rascunho recém-copiado tem o mesmo conteúdo da publicada")
  void copia_tem_o_mesmo_conteudo() {
    var draft = published.copyAsDraft(2);

    assertThat(draft.sameContentAs(published)).isTrue();

    draft.updateQuestion(
        draft.getQuestions().getFirst().id(),
        new Question.Draft(
            QuestionStatement.of("Outro enunciado"),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty()));

    assertThat(draft.sameContentAs(published)).isFalse();
  }
}
