package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetSurveyHealthUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkErrorsUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkVersionsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListSdkErrorsQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkErrorReportResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkVersionsResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyHealthQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyHealthResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.SdkErrorReportPresenter;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.SdkVersionPresenter;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.SurveyHealthPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}")
public class SdkHealthController implements SdkHealthSwagger {

  private final ListSdkVersionsUseCase listSdkVersions;
  private final ListSdkErrorsUseCase listSdkErrors;
  private final GetSurveyHealthUseCase getSurveyHealth;

  SdkHealthController(
      ListSdkVersionsUseCase listSdkVersions,
      ListSdkErrorsUseCase listSdkErrors,
      GetSurveyHealthUseCase getSurveyHealth) {
    this.listSdkVersions = listSdkVersions;
    this.listSdkErrors = listSdkErrors;
    this.getSurveyHealth = getSurveyHealth;
  }

  @Override
  @GetMapping(value = "/sdk-versions", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SdkVersionsResponseDTO> versions(@PathVariable String applicationId) {
    var output = listSdkVersions.execute(new ListSdkVersionsUseCase.Input(applicationId));
    return ResponseEntity.ok(SdkVersionPresenter.present(output));
  }

  @Override
  @GetMapping(value = "/sdk-errors", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<SdkErrorReportResponseDTO>> errors(
      @PathVariable String applicationId, @Valid @ModelAttribute ListSdkErrorsQueryDTO query) {
    var output = listSdkErrors.execute(query.toInput(applicationId));
    return ResponseEntity.ok(SdkErrorReportPresenter.present(output));
  }

  @Override
  @GetMapping(value = "/surveys/{surveyId}/health", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SurveyHealthResponseDTO> surveyHealth(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @ModelAttribute SurveyHealthQueryDTO query) {
    var output = getSurveyHealth.execute(query.toInput(applicationId, surveyId));
    return ResponseEntity.ok(SurveyHealthPresenter.present(output));
  }
}
