package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.OpenSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.SubmitSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyDisplayResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.SurveyDisplayPresenter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/collect/displays")
public class CollectDisplayController implements CollectDisplaySwagger {

  private final OpenSurveyDisplayUseCase openSurveyDisplay;
  private final SubmitSurveyDisplayUseCase submitSurveyDisplay;

  CollectDisplayController(
      OpenSurveyDisplayUseCase openSurveyDisplay,
      SubmitSurveyDisplayUseCase submitSurveyDisplay) {
    this.openSurveyDisplay = openSurveyDisplay;
    this.submitSurveyDisplay = submitSurveyDisplay;
  }

  @Override
  @PostMapping
  public ResponseEntity<SurveyDisplayResponseDTO> open(
      @Valid @RequestBody OpenDisplayRequestDTO request, AuthenticatedApplication application) {

    var output = openSurveyDisplay.execute(request.toInput(application.applicationId()));
    var body = SurveyDisplayPresenter.present(output.display());

    if (!output.created()) {
      return ResponseEntity.ok(body);
    }

    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{displayId}")
            .buildAndExpand(body.displayId())
            .toUri();

    return ResponseEntity.created(location).body(body);
  }

  @Override
  @PostMapping("/{displayId}/submission")
  public ResponseEntity<Void> submit(
      @PathVariable String displayId,
      @Valid @RequestBody SubmissionRequestDTO request,
      AuthenticatedApplication application) {

    submitSurveyDisplay.execute(request.toInput(application.applicationId(), displayId));

    return ResponseEntity.noContent().build();
  }
}
