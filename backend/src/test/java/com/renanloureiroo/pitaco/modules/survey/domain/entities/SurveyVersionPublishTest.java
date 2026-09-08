package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.EventName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SamplingRate;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SurveyVersion — publicação")
class SurveyVersionPublishTest {

  private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

  private SurveyVersion version;

  @BeforeEach
  void setUp() {
    version = SurveyVersion.create(SurveyId.generate(), 1);
  }

  private void makePublishable() {
    version.addQuestion(
        new Question.Draft(
            QuestionStatement.of("O que achou?"),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty()));
    version.defineTrigger(
        new Trigger(
            EventName.of("checkout.completed"),
            TriggerWindow.of(NOW, null),
            SamplingRate.of(0.25)));
  }

  @Test
  @DisplayName("Publicar a versão 1 congela o conteúdo e abre o grupo de comparabilidade 1")
  void publica_a_versao_um() {
    makePublishable();

    var published = version.publish(NOW, Optional.empty(), Optional.empty(), Optional.empty());

    assertThat(published.getStatus()).isEqualTo(SurveyVersionStatus.PUBLISHED);
    assertThat(published.publishedAt()).contains(NOW);
    assertThat(published.getComparabilityGroup()).isEqualTo(1);
    assertThat(published.changeKind()).isEmpty();
    assertThat(published.changeSummary()).isEmpty();
    assertThat(published.isEditable()).isFalse();
  }

  @Test
  @DisplayName("Depois de publicada, a versão não aceita mais nenhuma escrita")
  void publicada_nao_aceita_escrita() {
    makePublishable();
    var question = version.getQuestions().getFirst();
    var published = version.publish(NOW, Optional.empty(), Optional.empty(), Optional.empty());
    var draft =
        new Question.Draft(
            QuestionStatement.of("Nova"),
            QuestionType.FREE_TEXT,
            true,
            List.of(),
            Optional.empty());
    var trigger =
        new Trigger(EventName.of("app.opened"), TriggerWindow.of(NOW, null), SamplingRate.of(1.0));

    assertThatThrownBy(() -> published.addQuestion(draft)).satisfies(congelado());
    assertThatThrownBy(() -> published.removeQuestion(question.id())).satisfies(congelado());
    assertThatThrownBy(() -> published.defineTrigger(trigger)).satisfies(congelado());
    assertThatThrownBy(
            () -> published.publish(NOW, Optional.empty(), Optional.empty(), Optional.empty()))
        .satisfies(congelado());
  }

  @Test
  @DisplayName("Publicar com impedimentos é recusado, e nada é congelado")
  void recusa_publicar_com_impedimentos() {
    assertThatThrownBy(
            () -> version.publish(NOW, Optional.empty(), Optional.empty(), Optional.empty()))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.BUSINESS_RULE);
              assertThat(domainError.code()).isEqualTo("survey.not_publishable");
            });

    assertThat(version.getStatus()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(version.publishedAt()).isEmpty();
  }

  private static java.util.function.Consumer<Throwable> congelado() {
    return error -> {
      assertThat(error).isInstanceOf(DomainException.class);
      assertThat(((DomainException) error).code()).isEqualTo("survey.content_frozen");
    };
  }
}
