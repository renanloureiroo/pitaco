package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplayDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.DisplayDetailPresenter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Rota plana sob a aplicação, não aninhada na pesquisa: o identificador de exibição é chave
// primária global e a exibição já determina sua pesquisa (D-09).
@RestController
@RequestMapping("/applications/{applicationId}/displays")
public class DisplayController implements DisplaySwagger {

  private final GetSurveyDisplayUseCase getSurveyDisplayUseCase;

  DisplayController(GetSurveyDisplayUseCase getSurveyDisplayUseCase) {
    this.getSurveyDisplayUseCase = getSurveyDisplayUseCase;
  }

  @Override
  @GetMapping("/{displayId}")
  public ResponseEntity<DisplayDetailResponseDTO> get(
      @PathVariable String applicationId, @PathVariable String displayId) {
    var output =
        getSurveyDisplayUseCase.execute(
            new GetSurveyDisplayUseCase.Input(applicationId, displayId));

    return ResponseEntity.ok(DisplayDetailPresenter.present(output));
  }
}
