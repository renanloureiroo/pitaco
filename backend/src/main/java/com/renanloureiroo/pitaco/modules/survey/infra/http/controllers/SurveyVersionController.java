package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DiscardSurveyVersionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetSurveyVersionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetVersionComparabilityUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListSurveyVersionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.OpenSurveyVersionUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ListSurveyVersionsQueryDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.VersionComparabilityResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.GetSurveyVersionPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.ListSurveyVersionsPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.SurveyVersionPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.VersionComparabilityPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/versions")
public class SurveyVersionController implements SurveyVersionControllerSwagger {

  private final ListSurveyVersionsUseCase listSurveyVersionsUseCase;
  private final OpenSurveyVersionUseCase openSurveyVersionUseCase;
  private final DiscardSurveyVersionUseCase discardSurveyVersionUseCase;
  private final GetSurveyVersionUseCase getSurveyVersionUseCase;
  private final GetVersionComparabilityUseCase getVersionComparabilityUseCase;

  SurveyVersionController(
      ListSurveyVersionsUseCase listSurveyVersionsUseCase,
      OpenSurveyVersionUseCase openSurveyVersionUseCase,
      DiscardSurveyVersionUseCase discardSurveyVersionUseCase,
      GetSurveyVersionUseCase getSurveyVersionUseCase,
      GetVersionComparabilityUseCase getVersionComparabilityUseCase) {
    this.listSurveyVersionsUseCase = listSurveyVersionsUseCase;
    this.openSurveyVersionUseCase = openSurveyVersionUseCase;
    this.discardSurveyVersionUseCase = discardSurveyVersionUseCase;
    this.getSurveyVersionUseCase = getSurveyVersionUseCase;
    this.getVersionComparabilityUseCase = getVersionComparabilityUseCase;
  }

  // produces fixa o media type porque @Schema(implementation) não expressa
  // PageResponseDTO<SurveyVersionResponseDTO>.
  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<SurveyVersionResponseDTO>> list(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @ModelAttribute ListSurveyVersionsQueryDTO query) {
    var output = listSurveyVersionsUseCase.execute(query.toInput(applicationId, surveyId));

    return ResponseEntity.ok(ListSurveyVersionsPresenter.present(output));
  }

  @Override
  @PostMapping
  public ResponseEntity<SurveyVersionResponseDTO> open(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output =
        openSurveyVersionUseCase.execute(
            new OpenSurveyVersionUseCase.Input(applicationId, surveyId));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{number}")
            .buildAndExpand(output.number())
            .toUri();

    return ResponseEntity.created(location).body(SurveyVersionPresenter.present(output));
  }

  @Override
  @DeleteMapping("/draft")
  public ResponseEntity<Void> discardDraft(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    discardSurveyVersionUseCase.execute(
        new DiscardSurveyVersionUseCase.Input(applicationId, surveyId));

    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/comparability")
  public ResponseEntity<VersionComparabilityResponseDTO> comparability(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output =
        getVersionComparabilityUseCase.execute(
            new GetVersionComparabilityUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(VersionComparabilityPresenter.present(output));
  }

  @Override
  @GetMapping("/{number}")
  public ResponseEntity<SurveyVersionDetailResponseDTO> get(
      @PathVariable String applicationId, @PathVariable String surveyId, @PathVariable int number) {
    var output =
        getSurveyVersionUseCase.execute(
            new GetSurveyVersionUseCase.Input(applicationId, surveyId, number));

    return ResponseEntity.ok(GetSurveyVersionPresenter.present(output));
  }
}
