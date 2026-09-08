package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.AddQuestionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.RemoveQuestionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ReorderQuestionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.UpdateQuestionUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ReorderQuestionsRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.UpdateQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.QuestionPresenter;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/questions")
public class QuestionController implements QuestionControllerSwagger {

  private final AddQuestionUseCase addQuestionUseCase;
  private final UpdateQuestionUseCase updateQuestionUseCase;
  private final RemoveQuestionUseCase removeQuestionUseCase;
  private final ReorderQuestionsUseCase reorderQuestionsUseCase;

  QuestionController(
      AddQuestionUseCase addQuestionUseCase,
      UpdateQuestionUseCase updateQuestionUseCase,
      RemoveQuestionUseCase removeQuestionUseCase,
      ReorderQuestionsUseCase reorderQuestionsUseCase) {
    this.addQuestionUseCase = addQuestionUseCase;
    this.updateQuestionUseCase = updateQuestionUseCase;
    this.removeQuestionUseCase = removeQuestionUseCase;
    this.reorderQuestionsUseCase = reorderQuestionsUseCase;
  }

  @Override
  @PostMapping
  public ResponseEntity<QuestionResponseDTO> add(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @RequestBody AddQuestionRequestDTO request) {
    var output = addQuestionUseCase.execute(request.toInput(applicationId, surveyId));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(output.id())
            .toUri();

    return ResponseEntity.created(location).body(QuestionPresenter.present(output));
  }

  @Override
  @PutMapping("/{questionId}")
  public ResponseEntity<QuestionResponseDTO> update(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @PathVariable String questionId,
      @Valid @RequestBody UpdateQuestionRequestDTO request) {
    var output =
        updateQuestionUseCase.execute(request.toInput(applicationId, surveyId, questionId));

    return ResponseEntity.ok(QuestionPresenter.present(output));
  }

  @Override
  @DeleteMapping("/{questionId}")
  public ResponseEntity<Void> remove(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @PathVariable String questionId) {
    removeQuestionUseCase.execute(
        new RemoveQuestionUseCase.Input(applicationId, surveyId, questionId));

    return ResponseEntity.noContent().build();
  }

  @Override
  @PutMapping("/order")
  public ResponseEntity<List<QuestionResponseDTO>> reorder(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @RequestBody ReorderQuestionsRequestDTO request) {
    var output = reorderQuestionsUseCase.execute(request.toInput(applicationId, surveyId));

    return ResponseEntity.ok(QuestionPresenter.presentAll(output));
  }
}
