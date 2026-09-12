package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetQuotaProgressUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.QuotaProgressResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.QuotaProgressPresenter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/quota-progress")
public class QuotaProgressController implements QuotaProgressSwagger {

  private final GetQuotaProgressUseCase getQuotaProgressUseCase;

  QuotaProgressController(GetQuotaProgressUseCase getQuotaProgressUseCase) {
    this.getQuotaProgressUseCase = getQuotaProgressUseCase;
  }

  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<QuotaProgressResponseDTO> get(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output =
        getQuotaProgressUseCase.execute(new GetQuotaProgressUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(QuotaProgressPresenter.present(output));
  }
}
