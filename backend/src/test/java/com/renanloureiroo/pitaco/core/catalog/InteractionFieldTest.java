package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionField")
class InteractionFieldTest {

  @Test
  @DisplayName("Contagem aceita zero e recusa negativo")
  void contagem() {
    var field = InteractionField.count("length");

    assertThat(field.normalize(0)).contains(0L);
    assertThat(field.normalize(-1)).isEmpty();
    assertThat(field.normalize((long) Integer.MAX_VALUE)).contains((long) Integer.MAX_VALUE);
    assertThat(field.normalize((long) Integer.MAX_VALUE + 1)).isEmpty();
  }

  @Test
  @DisplayName("Posição e visita começam em 1")
  void positivo() {
    var field = InteractionField.positive("visit");

    assertThat(field.normalize(1)).contains(1L);
    assertThat(field.normalize(0)).isEmpty();
  }

  @Test
  @DisplayName("Duração aceita inteiro longo, e o double inteiro do JavaScript, mas não fração")
  void duracao() {
    var field = InteractionField.duration("activeMs");

    assertThat(field.normalize(5_000_000_000L)).contains(5_000_000_000L);
    assertThat(field.normalize(1200.0)).contains(1200L);
    assertThat(field.normalize(new BigDecimal("1200.00"))).contains(1200L);
    assertThat(field.normalize(1200.5)).isEmpty();
    assertThat(field.normalize(-1L)).isEmpty();
    assertThat(field.normalize(BigInteger.TWO.pow(70))).isEmpty();
    assertThat(field.normalize("1200")).isEmpty();
  }

  @Test
  @DisplayName("Escolha aceita só os valores listados, exatamente")
  void escolha() {
    var field = InteractionField.choice("via", "swipe", "backdrop");

    assertThat(field.normalize("swipe")).contains("swipe");
    assertThat(field.normalize("SWIPE")).isEmpty();
    assertThat(field.normalize("teleport")).isEmpty();
    assertThat(field.normalize(1)).isEmpty();
  }

  @Test
  @DisplayName("Chave de pergunta precisa ser a chave estável, um UUID")
  void chave() {
    var field = InteractionField.questionKey("toKey");
    var key = QuestionKey.generate().value();

    assertThat(field.normalize(key)).contains(key);
    assertThat(field.normalize("q-nps")).isEmpty();
  }

  @Test
  @DisplayName("Valor de resposta é o value de uma opção, até 120 caracteres, ou um inteiro")
  void valor_de_resposta() {
    var field = InteractionField.answerValue("value");

    assertThat(field.normalize("preco")).contains("preco");
    assertThat(field.normalize("  preco  ")).contains("preco");
    assertThat(field.normalize("a".repeat(120))).contains("a".repeat(120));
    assertThat(field.normalize("a".repeat(121))).isEmpty();
    assertThat(field.normalize("   ")).isEmpty();
    assertThat(field.normalize(7)).contains(7L);
    assertThat(field.normalize(7.5)).isEmpty();
    assertThat(field.normalize(true)).isEmpty();
  }

  @Test
  @DisplayName("Nome de evento segue a regra do disparo")
  void nome_de_evento() {
    var field = InteractionField.eventName("triggerEvent");

    assertThat(field.normalize("checkout.completed")).contains("checkout.completed");
    assertThat(field.normalize("Checkout Concluído")).isEmpty();
  }

  @Test
  @DisplayName("Sinalizador é só booleano")
  void sinalizador() {
    var field = InteractionField.flag("answered");

    assertThat(field.normalize(false)).contains(false);
    assertThat(field.normalize("true")).isEmpty();
  }

  @Test
  @DisplayName("Ausente é ausente em todo tipo")
  void ausente() {
    for (var kind : InteractionFieldKind.values()) {
      assertThat(new InteractionField("x", kind, java.util.List.of("a")).normalize(null)).as(kind.name()).isEmpty();
    }
  }
}
