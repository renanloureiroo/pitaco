package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListDisplaysQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListRespondentsQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDisplayResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.RespondentDisplayPresenter;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.RespondentPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}/respondents")
public class RespondentController implements RespondentSwagger {

  private final ListRespondentsUseCase listRespondentsUseCase;
  private final ListRespondentDisplaysUseCase listRespondentDisplaysUseCase;

  RespondentController(
      ListRespondentsUseCase listRespondentsUseCase,
      ListRespondentDisplaysUseCase listRespondentDisplaysUseCase) {
    this.listRespondentsUseCase = listRespondentsUseCase;
    this.listRespondentDisplaysUseCase = listRespondentDisplaysUseCase;
  }

  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<RespondentResponseDTO>> list(
      @PathVariable String applicationId,
      @Valid @ModelAttribute ListRespondentsQueryDTO query) {
    var output = listRespondentsUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(RespondentPresenter.present(output));
  }

  // Reaproveita o DTO da listagem por pesquisa; versionId não se aplica aqui (FR-025).
  @Override
  @GetMapping(value = "/{respondentId}/displays", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<RespondentDisplayResponseDTO>> listDisplays(
      @PathVariable String applicationId,
      @PathVariable String respondentId,
      @Valid @ModelAttribute ListDisplaysQueryDTO query) {
    var output =
        listRespondentDisplaysUseCase.execute(
            query.toRespondentInput(applicationId, respondentId));

    return ResponseEntity.ok(RespondentDisplayPresenter.present(output));
  }
}
