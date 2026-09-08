package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.EndSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListStateTransitionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.PauseSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ResumeSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyStateTransitionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.ListStateTransitionsPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.SurveyPresenter;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sub-recursos de comando em vez de um PUT .../state: cada transição tem seu próprio conjunto
// de erros, e um endpoint só obrigaria a documentar a união dos três.
@RestController
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}")
public class SurveyLifecycleController implements SurveyLifecycleControllerSwagger {

  private final PauseSurveyUseCase pauseSurveyUseCase;
  private final ResumeSurveyUseCase resumeSurveyUseCase;
  private final EndSurveyUseCase endSurveyUseCase;
  private final ListStateTransitionsUseCase listStateTransitionsUseCase;

  SurveyLifecycleController(
      PauseSurveyUseCase pauseSurveyUseCase,
      ResumeSurveyUseCase resumeSurveyUseCase,
      EndSurveyUseCase endSurveyUseCase,
      ListStateTransitionsUseCase listStateTransitionsUseCase) {
    this.pauseSurveyUseCase = pauseSurveyUseCase;
    this.resumeSurveyUseCase = resumeSurveyUseCase;
    this.endSurveyUseCase = endSurveyUseCase;
    this.listStateTransitionsUseCase = listStateTransitionsUseCase;
  }

  @Override
  @PostMapping("/pause")
  public ResponseEntity<SurveyResponseDTO> pause(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output = pauseSurveyUseCase.execute(new PauseSurveyUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(SurveyPresenter.present(output));
  }

  @Override
  @PostMapping("/resume")
  public ResponseEntity<SurveyResponseDTO> resume(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output =
        resumeSurveyUseCase.execute(new ResumeSurveyUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(SurveyPresenter.present(output));
  }

  @Override
  @PostMapping("/end")
  public ResponseEntity<SurveyResponseDTO> end(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output = endSurveyUseCase.execute(new EndSurveyUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(SurveyPresenter.present(output));
  }

  @Override
  @GetMapping("/transitions")
  public ResponseEntity<List<SurveyStateTransitionResponseDTO>> transitions(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output =
        listStateTransitionsUseCase.execute(
            new ListStateTransitionsUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(ListStateTransitionsPresenter.present(output));
  }
}
