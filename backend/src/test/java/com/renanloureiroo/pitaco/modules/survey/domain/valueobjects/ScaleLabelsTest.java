package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ScaleLabels")
class ScaleLabelsTest {

  @Test
  @DisplayName("Rótulo em branco vira ausente, e o preenchido perde o espaço das pontas")
  void normaliza() {
    var labels = ScaleLabels.of("  Nada provável ", "   ");

    assertThat(labels.min()).contains("Nada provável");
    assertThat(labels.max()).isEmpty();
    assertThat(ScaleLabels.of(null, null).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("O rótulo tem até 60 caracteres")
  void limite() {
    assertThat(ScaleLabels.of("x".repeat(60), null).min()).isPresent();

    assertThatThrownBy(() -> ScaleLabels.of("x".repeat(61), null))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> {
              assertThat(error.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(error.code()).isEqualTo("question.scale_labels_invalid");
            });
  }
}
