package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListObservedEventsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListObservedEventsQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedEventResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.ObservedEventPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}/events")
public class ObservedEventController implements ObservedEventSwagger {

  private final ListObservedEventsUseCase listObservedEventsUseCase;

  ObservedEventController(ListObservedEventsUseCase listObservedEventsUseCase) {
    this.listObservedEventsUseCase = listObservedEventsUseCase;
  }

  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<ObservedEventResponseDTO>> list(
      @PathVariable String applicationId,
      @Valid @ModelAttribute ListObservedEventsQueryDTO query) {
    var output = listObservedEventsUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(ObservedEventPresenter.present(output));
  }
}
