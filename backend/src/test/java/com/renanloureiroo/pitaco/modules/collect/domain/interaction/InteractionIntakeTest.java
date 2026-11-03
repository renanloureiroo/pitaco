package com.renanloureiroo.pitaco.modules.collect.domain.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionIntake.Discarded;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionIntake.Readable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionIntake")
class InteractionIntakeTest {

  private static final DisplayId DISPLAY = DisplayId.of(UUID.randomUUID().toString());
  private static final Instant AT = Instant.parse("2026-09-12T13:45:00Z");
  private static final QuestionKey CHOICE = QuestionKey.generate();
  private static final QuestionKey TEXT = QuestionKey.generate();
  private static final Map<QuestionKey, QuestionType> QUESTIONS =
      Map.of(CHOICE, QuestionType.SINGLE_CHOICE, TEXT, QuestionType.FREE_TEXT);

  private static InteractionDraft draft(String type, long seq, String questionKey, Map<String, Object> data) {
    return new InteractionDraft(
        Optional.of(1),
        Optional.of(type),
        Optional.of(DISPLAY.value()),
        Optional.of(seq),
        Optional.of(AT),
        Optional.of(10L),
        Optional.ofNullable(questionKey),
        data);
  }

  private static InteractionIntake.Reading read(InteractionDraft draft) {
    return InteractionIntake.read(draft, DISPLAY, QUESTIONS, AT);
  }

  private static InteractionEvent readable(String type, int seq) {
    return ((Readable) read(draft(type, seq, null, Map.of()))).event();
  }

  @Test
  @DisplayName("Evento completo e conhecido é legível, com o payload fechado")
  void legivel() {
    var reading = read(draft("answer_selected", 3, CHOICE.value(), Map.of("value", "yes", "label", "Sim")));

    assertThat(reading).isInstanceOfSatisfying(Readable.class, ok -> {
      assertThat(ok.event().type()).isEqualTo(InteractionEventType.ANSWER_SELECTED);
      assertThat(ok.event().seq()).isEqualTo(3);
      assertThat(ok.event().data()).containsExactly(Map.entry("value", "yes"));
    });
  }

  @Test
  @DisplayName("Tipo desconhecido é descartado pelo tipo, mesmo com envelope faltando")
  void tipo_desconhecido() {
    var unknown =
        new InteractionDraft(
            Optional.empty(), Optional.of("survey_teleported"), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Map.of());

    assertThat(read(unknown)).isEqualTo(new Discarded(DiscardReason.UNKNOWN_TYPE));
    assertThat(read(draft("survey_teleported", 1, null, Map.of()))).isEqualTo(new Discarded(DiscardReason.UNKNOWN_TYPE));
  }

  @Test
  @DisplayName("Catálogo mais novo com tipo conhecido é aceito; o que o servidor não conhece sai")
  void catalogo_mais_novo() {
    var newer =
        new InteractionDraft(
            Optional.of(2), Optional.of("survey_backgrounded"), Optional.empty(), Optional.of(1L),
            Optional.of(AT), Optional.of(0L), Optional.empty(), Map.of("reason", "phone_call"));

    assertThat(read(newer)).isInstanceOfSatisfying(Readable.class, ok -> {
      assertThat(ok.event().catalogVersion()).isEqualTo(2);
      assertThat(ok.event().data()).isEmpty();
    });
  }

  @Test
  @DisplayName("Envelope incompleto é descartado: sem seq, sem instante, sem tempo, sem versão")
  void envelope_incompleto() {
    var base = draft("survey_backgrounded", 1, null, Map.of());

    for (var incomplete :
        List.of(
            new InteractionDraft(Optional.empty(), base.type(), base.displayId(), base.seq(), base.occurredAt(), base.elapsedMs(), base.questionKey(), base.data()),
            new InteractionDraft(base.catalogVersion(), base.type(), base.displayId(), Optional.empty(), base.occurredAt(), base.elapsedMs(), base.questionKey(), base.data()),
            new InteractionDraft(base.catalogVersion(), base.type(), base.displayId(), base.seq(), Optional.empty(), base.elapsedMs(), base.questionKey(), base.data()),
            new InteractionDraft(base.catalogVersion(), base.type(), base.displayId(), base.seq(), base.occurredAt(), Optional.empty(), base.questionKey(), base.data()))) {
      assertThat(read(incomplete)).isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
    }
  }

  @Test
  @DisplayName("Seq fora da faixa de inteiro e seq zero são envelope inválido")
  void seq_fora_da_faixa() {
    assertThat(read(draft("survey_backgrounded", (long) Integer.MAX_VALUE + 1, null, Map.of())))
        .isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
    assertThat(read(draft("survey_backgrounded", 0, null, Map.of())))
        .isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
    assertThat(read(draft("survey_backgrounded", Integer.MAX_VALUE, null, Map.of())))
        .isInstanceOf(Readable.class);
  }

  @Test
  @DisplayName("displayId de outra exibição é envelope inválido; ausente vale o do caminho")
  void outra_exibicao() {
    var other =
        new InteractionDraft(
            Optional.of(1), Optional.of("survey_backgrounded"), Optional.of(UUID.randomUUID().toString()),
            Optional.of(1L), Optional.of(AT), Optional.of(0L), Optional.empty(), Map.of());
    var absent =
        new InteractionDraft(
            Optional.of(1), Optional.of("survey_backgrounded"), Optional.empty(),
            Optional.of(1L), Optional.of(AT), Optional.of(0L), Optional.empty(), Map.of());
    var upper =
        new InteractionDraft(
            Optional.of(1), Optional.of("survey_backgrounded"), Optional.of(DISPLAY.value().toUpperCase()),
            Optional.of(1L), Optional.of(AT), Optional.of(0L), Optional.empty(), Map.of());

    assertThat(read(other)).isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
    assertThat(read(absent)).isInstanceOf(Readable.class);
    assertThat(read(upper)).isInstanceOf(Readable.class);
  }

  @Test
  @DisplayName("Evento de pergunta sem chave, ou com chave malformada, é envelope inválido")
  void pergunta_sem_chave() {
    assertThat(read(draft("question_viewed", 1, null, Map.of()))).isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
    assertThat(read(draft("question_viewed", 1, "q-nps", Map.of()))).isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
  }

  @Test
  @DisplayName("Pergunta que não está na versão exibida é descartada como desconhecida")
  void pergunta_desconhecida() {
    assertThat(read(draft("question_viewed", 1, QuestionKey.generate().value(), Map.of())))
        .isEqualTo(new Discarded(DiscardReason.UNKNOWN_QUESTION));
  }

  @Test
  @DisplayName("Escolha registrada em texto livre é descartada: o valor seria o texto digitado")
  void escolha_em_texto_livre() {
    assertThat(read(draft("answer_selected", 1, TEXT.value(), Map.of("value", "meu email"))))
        .isEqualTo(new Discarded(DiscardReason.INVALID_ENVELOPE));
    assertThat(read(draft("text_edited", 1, TEXT.value(), Map.of("length", 9, "text", "meu email"))))
        .isInstanceOfSatisfying(Readable.class, ok -> assertThat(ok.event().data()).containsExactly(Map.entry("length", 9L)));
  }

  @Test
  @DisplayName("O primeiro de cada seq vence, no lote e contra o gravado")
  void repetidos() {
    var first = readable("survey_backgrounded", 2);
    var repeated = readable("survey_foregrounded", 2);
    var stored = readable("survey_presented", 1);
    var fresh = readable("survey_completed", 3);

    var admission = InteractionIntake.admit(List.of(first, repeated, stored, fresh), Set.of(1), 1, 500);

    assertThat(admission.accepted()).containsExactly(first, fresh);
    assertThat(admission.duplicated()).isEqualTo(2);
    assertThat(admission.overLimit()).isZero();
  }

  @Test
  @DisplayName("O teto corta pela ordem de seq, contando o que já está gravado: cabe exatamente o que falta")
  void teto() {
    var events = IntStream.of(5, 3, 4, 6).mapToObj(seq -> readable("survey_backgrounded", seq)).toList();

    var admission = InteractionIntake.admit(events, Set.of(), 8, 10);

    assertThat(admission.accepted()).extracting(InteractionEvent::seq).containsExactly(3, 4);
    assertThat(admission.overLimit()).isEqualTo(2);
  }

  @Test
  @DisplayName("Com o teto já atingido, nada entra; acima dele também não")
  void teto_atingido() {
    var events = List.of(readable("survey_backgrounded", 11));

    assertThat(InteractionIntake.admit(events, Set.of(), 10, 10).overLimit()).isOne();
    assertThat(InteractionIntake.admit(events, Set.of(), 12, 10).accepted()).isEmpty();
  }
}
