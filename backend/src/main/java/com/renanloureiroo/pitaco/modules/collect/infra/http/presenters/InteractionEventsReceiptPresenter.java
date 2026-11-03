package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.RecordInteractionEventsUseCase;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.DiscardReason;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsReceiptDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsReceiptDTO.DiscardedEventsDTO;

public final class InteractionEventsReceiptPresenter {

  private InteractionEventsReceiptPresenter() {}

  public static InteractionEventsReceiptDTO present(RecordInteractionEventsUseCase.Output output) {
    return new InteractionEventsReceiptDTO(
        output.accepted(),
        output.duplicated(),
        new DiscardedEventsDTO(
            output.discarded(DiscardReason.DISPLAY_UNAVAILABLE),
            output.discarded(DiscardReason.OUTSIDE_WINDOW),
            output.discarded(DiscardReason.UNKNOWN_TYPE),
            output.discarded(DiscardReason.INVALID_ENVELOPE),
            output.discarded(DiscardReason.UNKNOWN_QUESTION),
            output.discarded(DiscardReason.OVER_LIMIT)));
  }
}
