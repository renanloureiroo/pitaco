package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AnswerValue")
class AnswerValueTest {

  @Nested
  @DisplayName("Texto livre")
  class Texto {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void recusa_texto_vazio(String blank) {
      assertThatThrownBy(() -> AnswerText.of(blank)).satisfies(erro("answer.text_invalid"));
    }

    @Test
    void aceita_o_limite_de_dois_mil_caracteres() {
      assertThat(AnswerText.of("a".repeat(2000)).value()).hasSize(2000);
    }

    @Test
    void recusa_acima_do_limite() {
      assertThatThrownBy(() -> AnswerText.of("a".repeat(2001)))
          .satisfies(erro("answer.text_invalid"));
    }

    @Test
    void descarta_o_espaco_em_volta() {
      assertThat(AnswerText.of("  achei caro  ").value()).isEqualTo("achei caro");
    }
  }

  @Nested
  @DisplayName("Numérico")
  class Numerico {

    @Test
    void guarda_o_inteiro_informado() {
      assertThat(new AnswerValue.NumericValue(9).value()).isEqualTo(9);
    }
  }

  @Nested
  @DisplayName("Escolha")
  class Escolha {

    @Test
    void recusa_conjunto_vazio() {
      assertThatThrownBy(() -> new AnswerValue.ChoiceValue(List.of()))
          .satisfies(erro("answer.choice_invalid"));
    }

    @Test
    void recusa_opcao_repetida() {
      assertThatThrownBy(() -> new AnswerValue.ChoiceValue(List.of("a", "a")))
          .satisfies(erro("answer.choice_invalid"));
    }

    @Test
    void preserva_a_ordem_informada() {
      assertThat(new AnswerValue.ChoiceValue(List.of("b", "a")).options()).containsExactly("b", "a");
    }
  }

  private static org.assertj.core.api.ThrowingConsumer<Throwable> erro(String code) {
    return error -> {
      assertThat(error).isInstanceOf(DomainException.class);
      assertThat(((DomainException) error).code()).isEqualTo(code);
    };
  }
}
