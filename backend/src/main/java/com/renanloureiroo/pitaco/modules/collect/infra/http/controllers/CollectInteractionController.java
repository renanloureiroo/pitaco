package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.RecordInteractionEventsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsReceiptDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.InteractionEventsReceiptPresenter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/collect/displays")
public class CollectInteractionController implements CollectInteractionSwagger {

  private final RecordInteractionEventsUseCase recordInteractionEvents;

  CollectInteractionController(RecordInteractionEventsUseCase recordInteractionEvents) {
    this.recordInteractionEvents = recordInteractionEvents;
  }

  @Override
  @PostMapping("/{displayId}/events")
  public ResponseEntity<InteractionEventsReceiptDTO> record(
      @PathVariable String displayId,
      @Valid @RequestBody InteractionEventsRequestDTO request,
      AuthenticatedApplication application) {
    var output =
        recordInteractionEvents.execute(request.toInput(application.applicationId(), displayId));

    return ResponseEntity.accepted().body(InteractionEventsReceiptPresenter.present(output));
  }
}
