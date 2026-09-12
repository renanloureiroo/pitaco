package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListObservedAttributesUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListObservedAttributesQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedAttributeResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.ObservedAttributePresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}/attributes")
public class ObservedAttributeController implements ObservedAttributeSwagger {

  private final ListObservedAttributesUseCase listObservedAttributesUseCase;

  ObservedAttributeController(ListObservedAttributesUseCase listObservedAttributesUseCase) {
    this.listObservedAttributesUseCase = listObservedAttributesUseCase;
  }

  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<ObservedAttributeResponseDTO>> list(
      @PathVariable String applicationId,
      @Valid @ModelAttribute ListObservedAttributesQueryDTO query) {
    var output = listObservedAttributesUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(ObservedAttributePresenter.present(output));
  }
}
