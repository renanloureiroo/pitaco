package com.renanloureiroo.pitaco.modules.collect.domain.interaction;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Evento a evento, nunca o lote: um evento ruim é descartado e contado, e os demais seguem. Um 400
// faria o SDK jogar fora o lote inteiro, inclusive o que estava certo.
public final class InteractionIntake {

  private InteractionIntake() {}

  public sealed interface Reading permits Readable, Discarded {}

  public record Readable(InteractionEvent event) implements Reading {}

  public record Discarded(DiscardReason reason) implements Reading {}

  public record Admission(List<InteractionEvent> accepted, int duplicated, int overLimit) {}

  // O tipo vem antes do envelope: um SDK mais novo pode mandar tipo que o servidor não conhece com
  // envelope que ele também não conhece, e o motivo contado precisa ser o tipo.
  public static Reading read(
      InteractionDraft draft,
      DisplayId displayId,
      Map<QuestionKey, QuestionType> questions,
      Instant receivedAt) {
    var type = draft.type().flatMap(InteractionEventType::fromWire);
    if (type.isEmpty()) {
      return new Discarded(DiscardReason.UNKNOWN_TYPE);
    }

    var claimsOtherDisplay =
        draft.displayId().filter(claimed -> !claimed.equalsIgnoreCase(displayId.value()));
    if (claimsOtherDisplay.isPresent() || !hasEnvelope(draft)) {
      return new Discarded(DiscardReason.INVALID_ENVELOPE);
    }

    Optional<QuestionKey> key = Optional.empty();
    if (type.get().questionScoped()) {
      key = draft.questionKey().flatMap(InteractionIntake::questionKeyOf);
      if (key.isEmpty()) {
        return new Discarded(DiscardReason.INVALID_ENVELOPE);
      }
      var questionType = questions.get(key.get());
      if (questionType == null) {
        return new Discarded(DiscardReason.UNKNOWN_QUESTION);
      }
      // Escolha em texto livre não existe no catálogo, e o valor dela seria o texto digitado.
      if (questionType == QuestionType.FREE_TEXT && type.get().carriesAnswerValue()) {
        return new Discarded(DiscardReason.INVALID_ENVELOPE);
      }
    }

    try {
      return new Readable(
          new InteractionEvent(
              displayId,
              Math.toIntExact(draft.seq().get()),
              draft.catalogVersion().get(),
              type.get(),
              key,
              draft.occurredAt().get(),
              draft.elapsedMs().get(),
              draft.data(),
              receivedAt));
    } catch (DomainException | ArithmeticException invalid) {
      return new Discarded(DiscardReason.INVALID_ENVELOPE);
    }
  }

  // O primeiro evento de cada seq vence, no lote e contra o que já foi gravado; o teto corta pela
  // ordem de seq, então o que sobra é sempre o fim da exibição, nunca um buraco no meio.
  public static Admission admit(
      List<InteractionEvent> candidates, Set<Integer> storedSeqs, long storedCount, int maxPerDisplay) {
    var bySeq = new LinkedHashMap<Integer, InteractionEvent>();
    var duplicated = 0;

    for (var event : candidates) {
      if (storedSeqs.contains(event.seq()) || bySeq.putIfAbsent(event.seq(), event) != null) {
        duplicated++;
      }
    }

    var fresh =
        bySeq.values().stream().sorted(Comparator.comparingInt(InteractionEvent::seq)).toList();
    var room = (int) Math.max(0, Math.min(fresh.size(), maxPerDisplay - storedCount));

    return new Admission(List.copyOf(fresh.subList(0, room)), duplicated, fresh.size() - room);
  }

  private static boolean hasEnvelope(InteractionDraft draft) {
    return draft.catalogVersion().isPresent()
        && draft.seq().isPresent()
        && draft.occurredAt().isPresent()
        && draft.elapsedMs().isPresent();
  }

  private static Optional<QuestionKey> questionKeyOf(String raw) {
    try {
      return Optional.of(QuestionKey.of(raw));
    } catch (DomainException invalid) {
      return Optional.empty();
    }
  }
}
