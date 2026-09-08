package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.AddSegmentationRuleUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DefineTriggerUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.RemoveSegmentationRuleUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddSegmentationRuleRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DefineTriggerRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SegmentationRuleResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.TriggerResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.SegmentationRulePresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.TriggerPresenter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/trigger")
public class TriggerController implements TriggerControllerSwagger {

  private final DefineTriggerUseCase defineTriggerUseCase;
  private final AddSegmentationRuleUseCase addSegmentationRuleUseCase;
  private final RemoveSegmentationRuleUseCase removeSegmentationRuleUseCase;

  TriggerController(
      DefineTriggerUseCase defineTriggerUseCase,
      AddSegmentationRuleUseCase addSegmentationRuleUseCase,
      RemoveSegmentationRuleUseCase removeSegmentationRuleUseCase) {
    this.defineTriggerUseCase = defineTriggerUseCase;
    this.addSegmentationRuleUseCase = addSegmentationRuleUseCase;
    this.removeSegmentationRuleUseCase = removeSegmentationRuleUseCase;
  }

  @Override
  @PutMapping
  public ResponseEntity<TriggerResponseDTO> define(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @RequestBody DefineTriggerRequestDTO request) {
    var output = defineTriggerUseCase.execute(request.toInput(applicationId, surveyId));

    return ResponseEntity.ok(TriggerPresenter.present(output));
  }

  @Override
  @PostMapping("/rules")
  public ResponseEntity<SegmentationRuleResponseDTO> addRule(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @RequestBody AddSegmentationRuleRequestDTO request) {
    var output = addSegmentationRuleUseCase.execute(request.toInput(applicationId, surveyId));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(output.id())
            .toUri();

    return ResponseEntity.created(location).body(SegmentationRulePresenter.present(output));
  }

  @Override
  @DeleteMapping("/rules/{ruleId}")
  public ResponseEntity<Void> removeRule(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @PathVariable String ruleId) {
    removeSegmentationRuleUseCase.execute(
        new RemoveSegmentationRuleUseCase.Input(applicationId, surveyId, ruleId));

    return ResponseEntity.noContent().build();
  }
}
