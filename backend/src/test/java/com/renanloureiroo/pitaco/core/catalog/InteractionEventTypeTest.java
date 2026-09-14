package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InteractionEventType")
class InteractionEventTypeTest {

  @Test
  @DisplayName("O catálogo versão 1 tem exatamente os dezoito tipos do contrato, nesta ordem")
  void dezoito_tipos() {
    assertThat(Arrays.stream(InteractionEventType.values()).map(InteractionEventType::wire))
        .containsExactly(
            "survey_presented",
            "question_viewed",
            "answer_selected",
            "answer_changed",
            "answer_deselected",
            "text_focused",
            "text_edited",
            "text_blurred",
            "validation_blocked",
            "question_skipped",
            "question_not_applicable",
            "navigated_next",
            "navigated_back",
            "question_left",
            "survey_backgrounded",
            "survey_foregrounded",
            "survey_dismissed",
            "survey_completed");
    assertThat(InteractionEventType.CATALOG_VERSION).isEqualTo(1);
  }

  @Test
  @DisplayName("Só os eventos de pesquisa inteira dispensam a chave da pergunta")
  void escopo_de_pergunta() {
    assertThat(Arrays.stream(InteractionEventType.values()).filter(type -> !type.questionScoped()))
        .containsExactly(
            InteractionEventType.SURVEY_PRESENTED,
            InteractionEventType.SURVEY_BACKGROUNDED,
            InteractionEventType.SURVEY_FOREGROUNDED,
            InteractionEventType.SURVEY_DISMISSED,
            InteractionEventType.SURVEY_COMPLETED);
  }

  @Test
  @DisplayName("O payload de cada tipo tem os campos da tabela do contrato")
  void campos_por_tipo() {
    var expected = new HashMap<InteractionEventType, List<String>>();
    expected.put(
        InteractionEventType.SURVEY_PRESENTED,
        List.of("presentation", "questionCount", "renderableCount", "triggerEvent"));
    expected.put(InteractionEventType.QUESTION_VIEWED, List.of("position", "visit", "from"));
    expected.put(InteractionEventType.ANSWER_SELECTED, List.of("value"));
    expected.put(InteractionEventType.ANSWER_CHANGED, List.of("from", "to"));
    expected.put(InteractionEventType.ANSWER_DESELECTED, List.of("value"));
    expected.put(InteractionEventType.TEXT_FOCUSED, List.of());
    expected.put(InteractionEventType.TEXT_EDITED, List.of("length"));
    expected.put(InteractionEventType.TEXT_BLURRED, List.of("length"));
    expected.put(InteractionEventType.VALIDATION_BLOCKED, List.of("reason"));
    expected.put(InteractionEventType.QUESTION_SKIPPED, List.of());
    expected.put(InteractionEventType.QUESTION_NOT_APPLICABLE, List.of("sourceKey"));
    expected.put(InteractionEventType.NAVIGATED_NEXT, List.of("toKey"));
    expected.put(InteractionEventType.NAVIGATED_BACK, List.of("toKey"));
    expected.put(
        InteractionEventType.QUESTION_LEFT,
        List.of("visit", "to", "durationMs", "activeMs", "answered"));
    expected.put(InteractionEventType.SURVEY_BACKGROUNDED, List.of());
    expected.put(InteractionEventType.SURVEY_FOREGROUNDED, List.of("backgroundMs"));
    expected.put(
        InteractionEventType.SURVEY_DISMISSED, List.of("via", "position", "answeredCount"));
    expected.put(
        InteractionEventType.SURVEY_COMPLETED,
        List.of("answeredCount", "skippedCount", "notApplicableCount", "activeMs"));

    for (var type : InteractionEventType.values()) {
      assertThat(type.fields()).as(type.wire()).extracting(InteractionField::name).containsExactlyElementsOf(expected.get(type));
    }
  }

  @Test
  @DisplayName("As vias de dispensa e os destinos de saída são os listados no contrato")
  void listas_fechadas() {
    assertThat(InteractionEventType.SURVEY_DISMISSED.field("via").orElseThrow().choices())
        .containsExactly(
            "close_button", "swipe", "backdrop", "hardware_back", "navigation", "programmatic");
    assertThat(InteractionEventType.QUESTION_LEFT.field("to").orElseThrow().choices())
        .containsExactly("next", "back", "dismiss", "complete");
    assertThat(InteractionEventType.QUESTION_VIEWED.field("from").orElseThrow().choices())
        .containsExactly("start", "next", "back");
    assertThat(InteractionEventType.SURVEY_PRESENTED.field("presentation").orElseThrow().choices())
        .containsExactly("bottom-sheet", "modal", "inline");
  }

  @ParameterizedTest
  @EnumSource(InteractionEventType.class)
  @DisplayName("Todo tipo é reconhecido pelo próprio nome no fio")
  void reconhece_pelo_nome(InteractionEventType type) {
    assertThat(InteractionEventType.fromWire(type.wire())).contains(type);
  }

  @ParameterizedTest
  @ValueSource(strings = {"survey_teleported", "SURVEY_PRESENTED", " survey_presented", ""})
  @DisplayName("Nome fora do catálogo, em outra caixa ou com espaço não é reconhecido")
  void nao_reconhece(String raw) {
    assertThat(InteractionEventType.fromWire(raw)).isEmpty();
  }

  @Test
  @DisplayName("Nome ausente não é reconhecido")
  void ausente() {
    assertThat(InteractionEventType.fromWire(null)).isEmpty();
  }

  @Test
  @DisplayName("Evento de texto guarda só o tamanho: texto, valor e qualquer outro campo somem")
  void texto_so_tamanho() {
    var sanitized =
        InteractionEventType.TEXT_EDITED.sanitize(
            Map.of("length", 12, "text", "meu telefone é 9999", "value", "x", "content", "y"));

    assertThat(sanitized).containsExactly(Map.entry("length", 12L));
  }

  @Test
  @DisplayName("Tamanho em texto não é tamanho: o campo some e o evento fica sem ele")
  void tamanho_como_texto() {
    assertThat(InteractionEventType.TEXT_BLURRED.sanitize(Map.of("length", "doze"))).isEmpty();
  }

  @Test
  @DisplayName("Campo extra é ignorado e os declarados saem na ordem do catálogo")
  void campo_extra_ignorado() {
    var sanitized =
        InteractionEventType.QUESTION_LEFT.sanitize(
            Map.of(
                "answered", true,
                "activeMs", 1200,
                "durationMs", 1500,
                "to", "next",
                "visit", 1,
                "mood", "happy"));

    assertThat(sanitized.keySet())
        .containsExactly("visit", "to", "durationMs", "activeMs", "answered");
  }

  @Test
  @DisplayName("Sem payload, nenhum campo")
  void sem_payload() {
    assertThat(InteractionEventType.SURVEY_DISMISSED.sanitize(null)).isEmpty();
  }

  @Test
  @DisplayName("Só as três escolhas carregam valor de resposta")
  void carregam_valor() {
    assertThat(Arrays.stream(InteractionEventType.values()).filter(InteractionEventType::carriesAnswerValue))
        .containsExactly(
            InteractionEventType.ANSWER_SELECTED,
            InteractionEventType.ANSWER_CHANGED,
            InteractionEventType.ANSWER_DESELECTED);
  }
}
