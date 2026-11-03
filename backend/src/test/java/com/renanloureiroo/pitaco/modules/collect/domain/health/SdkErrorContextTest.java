package com.renanloureiroo.pitaco.modules.collect.domain.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SdkErrorContext")
class SdkErrorContextTest {

  @Test
  @DisplayName("Mantém texto, número e booleano sob chaves permitidas, na ordem recebida")
  void mantem_o_permitido() {
    var raw = new LinkedHashMap<String, Object>();
    raw.put("questionType", "matrix");
    raw.put("httpStatus", 502);
    raw.put("durationMs", 1234L);
    raw.put("retried", true);
    raw.put("ratio", 0.5);

    var context = SdkErrorContext.sanitize(raw);

    assertThat(context.values())
        .containsExactly(
            Map.entry("questionType", "matrix"),
            Map.entry("httpStatus", 502),
            Map.entry("durationMs", 1234L),
            Map.entry("retried", true),
            Map.entry("ratio", 0.5));
    assertThat(context.discarded()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "email", "userId", "user_name", "phoneNumber", "deviceId", "respondentReference",
        "answerValue", "freeText", "authToken", "senha", "ip", "attributes"
      })
  @DisplayName("Chave com palavra de dado pessoal é descartada")
  void descarta_chave_pessoal(String key) {
    var context = SdkErrorContext.sanitize(Map.of(key, "x"));

    assertThat(context.values()).isEmpty();
    assertThat(context.discarded()).isEqualTo(1);
  }

  @Test
  @DisplayName("Palavra dentro de outra não conta: context e questionKey passam")
  void palavra_inteira() {
    var context = SdkErrorContext.sanitize(Map.of("context", "boot", "questionKey", "q1"));

    assertThat(context.values()).containsOnlyKeys("context", "questionKey");
  }

  @Test
  @DisplayName("Valor com cara de e-mail ou de número longo é descartado")
  void descarta_valor_pessoal() {
    var context =
        SdkErrorContext.sanitize(
            Map.of("screen", "fale com joao@exemplo.com", "stage", "(11) 98765-4321"));

    assertThat(context.values()).isEmpty();
    assertThat(context.discarded()).isEqualTo(2);
  }

  @Test
  @DisplayName("Lista, nulo e chave fora do formato são descartados")
  void descarta_forma_invalida() {
    var raw = new LinkedHashMap<String, Object>();
    raw.put("steps", List.of(1, 2));
    raw.put("missing", null);
    raw.put("1bad", "x");
    raw.put("com espaço", "x");

    var context = SdkErrorContext.sanitize(raw);

    assertThat(context.values()).isEmpty();
    assertThat(context.discarded()).isEqualTo(4);
  }

  @Test
  @DisplayName("Aceita um nível de objeto, e o segundo nível é descartado")
  void um_nivel_de_objeto() {
    var context =
        SdkErrorContext.sanitize(
            Map.of("render", Map.of("component", "Rating", "deep", Map.of("x", 1))));

    assertThat(context.values()).containsEntry("render", Map.of("component", "Rating"));
    assertThat(context.discarded()).isEqualTo(1);
  }

  @Test
  @DisplayName("Texto longo é cortado no limite")
  void corta_texto_longo() {
    var context = SdkErrorContext.sanitize(Map.of("stage", "a".repeat(500)));

    assertThat((String) context.values().get("stage")).hasSize(SdkErrorContext.MAX_STRING_LENGTH);
  }

  @Test
  @DisplayName("Passadas as chaves permitidas, o resto é descartado")
  void limite_de_chaves() {
    var raw = new LinkedHashMap<String, Object>();
    for (var index = 0; index < SdkErrorContext.MAX_KEYS + 5; index++) {
      raw.put("k" + index, index);
    }

    var context = SdkErrorContext.sanitize(raw);

    assertThat(context.values()).hasSize(SdkErrorContext.MAX_KEYS);
    assertThat(context.discarded()).isEqualTo(5);
  }

  @Test
  @DisplayName("O tamanho total tem teto, e o que estoura fica de fora")
  void teto_de_tamanho() {
    var raw = new LinkedHashMap<String, Object>();
    for (var index = 0; index < 15; index++) {
      raw.put("stage" + index, "b".repeat(190));
    }

    var context = SdkErrorContext.sanitize(raw);

    var size =
        context.values().entrySet().stream()
            .mapToInt(entry -> entry.getKey().length() + entry.getValue().toString().length())
            .sum();
    assertThat(size).isLessThanOrEqualTo(SdkErrorContext.MAX_TOTAL_SIZE);
    assertThat(context.discarded()).isPositive();
  }

  @Test
  @DisplayName("Contexto ausente é vazio")
  void ausente_e_vazio() {
    assertThat(SdkErrorContext.sanitize(null).values()).isEmpty();
  }
}
