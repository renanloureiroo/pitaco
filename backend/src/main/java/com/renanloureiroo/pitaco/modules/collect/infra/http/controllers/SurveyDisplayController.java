package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSurveyDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplaySummaryResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListDisplaysQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.DisplaySummaryPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/displays")
public class SurveyDisplayController implements SurveyDisplaySwagger {

  private final ListSurveyDisplaysUseCase listSurveyDisplaysUseCase;

  SurveyDisplayController(ListSurveyDisplaysUseCase listSurveyDisplaysUseCase) {
    this.listSurveyDisplaysUseCase = listSurveyDisplaysUseCase;
  }

  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<DisplaySummaryResponseDTO>> list(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @ModelAttribute ListDisplaysQueryDTO query) {
    var output = listSurveyDisplaysUseCase.execute(query.toInput(applicationId, surveyId));

    return ResponseEntity.ok(DisplaySummaryPresenter.present(output));
  }
}
