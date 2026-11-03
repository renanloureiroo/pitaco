package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.InteractionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.DiscardReason;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionDraft;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionIntake;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionIntake.Discarded;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionIntake.Readable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// Nada aqui vira 404 nem 422: exibição inexistente, de outra aplicação ou de aplicação inativa
// descarta o lote com a mesma resposta de quando grava, para não dizer ao portador de uma chave
// extraída do bundle que aquele identificador existe em algum lugar.
@Slf4j
public class RecordInteractionEventsUseCase
    implements UseCase<RecordInteractionEventsUseCase.Input, RecordInteractionEventsUseCase.Output> {

  public static final int MAX_BATCH_SIZE = 100;

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final SurveyDisplayRepository displays;
  private final InteractionEventRepository events;
  private final int maxPerDisplay;
  private final Duration acceptanceWindow;

  public RecordInteractionEventsUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      SurveyDisplayRepository displays,
      InteractionEventRepository events,
      int maxPerDisplay,
      Duration acceptanceWindow) {
    this.applications = applications;
    this.catalog = catalog;
    this.displays = displays;
    this.events = events;
    this.maxPerDisplay = maxPerDisplay;
    this.acceptanceWindow = acceptanceWindow;
  }

  public record Input(String applicationId, String displayId, List<InteractionDraft> events) {}

  public record Output(int accepted, int duplicated, Map<DiscardReason, Integer> discarded) {

    public Output {
      discarded = Collections.unmodifiableMap(new EnumMap<>(discarded));
    }

    public int discarded(DiscardReason reason) {
      return discarded.getOrDefault(reason, 0);
    }
  }

  // A contagem do teto e a gravação precisam do mesmo instante do banco, sob a trava da exibição.
  @Override
  @Transactional
  public Output execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());
    var received = input.events().size();

    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      return allDiscarded(applicationId, input, DiscardReason.DISPLAY_UNAVAILABLE);
    }

    var found = displayIdOf(input.displayId()).flatMap(id -> displays.findById(id, applicationId));
    if (found.isEmpty()) {
      return allDiscarded(applicationId, input, DiscardReason.DISPLAY_UNAVAILABLE);
    }

    var display = found.get();
    var now = Instant.now();
    if (now.isAfter(display.getOpenedAt().plus(acceptanceWindow))) {
      return allDiscarded(applicationId, input, DiscardReason.OUTSIDE_WINDOW);
    }

    var questions = questionTypesOf(display);
    var discarded = new EnumMap<DiscardReason, Integer>(DiscardReason.class);
    var readable = new ArrayList<InteractionEvent>();

    for (var draft : input.events()) {
      switch (InteractionIntake.read(draft, display.id(), questions, now)) {
        case Readable accepted -> readable.add(accepted.event());
        case Discarded rejected -> discarded.merge(rejected.reason(), 1, Integer::sum);
      }
    }

    var accepted = 0;
    var duplicated = 0;
    if (!readable.isEmpty()) {
      events.lockDisplay(display.id());
      var stored =
          events.storedSeqs(
              display.id(), readable.stream().map(InteractionEvent::seq).distinct().toList());
      var admission =
          InteractionIntake.admit(
              readable, stored, events.countByDisplay(display.id()), maxPerDisplay);

      events.saveAll(admission.accepted());
      accepted = admission.accepted().size();
      duplicated = admission.duplicated();
      if (admission.overLimit() > 0) {
        discarded.merge(DiscardReason.OVER_LIMIT, admission.overLimit(), Integer::sum);
      }
    }

    var output = new Output(accepted, duplicated, discarded);
    logReceipt(applicationId, display.id().value(), received, output);
    return output;
  }

  private Map<QuestionKey, QuestionType> questionTypesOf(SurveyDisplay display) {
    return catalog
        .contentOf(display.getVersionId())
        .map(
            survey ->
                survey.questions().stream()
                    .collect(
                        Collectors.toMap(
                            DeliverableQuestion::key, DeliverableQuestion::type, (a, b) -> a)))
        .orElse(Map.of());
  }

  private static Optional<DisplayId> displayIdOf(String raw) {
    try {
      return Optional.of(DisplayId.of(raw));
    } catch (DomainException malformed) {
      return Optional.empty();
    }
  }

  private static Output allDiscarded(
      ApplicationId applicationId, Input input, DiscardReason reason) {
    var output = new Output(0, 0, Map.of(reason, input.events().size()));
    logReceipt(applicationId, "-", input.events().size(), output);
    return output;
  }

  // Contagens e motivos, jamais o payload: o evento pode carregar valor de resposta.
  private static void logReceipt(
      ApplicationId applicationId, String displayId, int received, Output output) {
    log.info(
        "Eventos de interação recebidos application={} display={} recebidos={} aceitos={}"
            + " repetidos={} descartados={}",
        applicationId.value(),
        displayId,
        received,
        output.accepted(),
        output.duplicated(),
        output.discarded().entrySet().stream()
            .map(entry -> entry.getKey().wire() + ":" + entry.getValue())
            .collect(Collectors.joining(",", "[", "]")));
  }
}
