package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.FindEligibleSurveyUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.presenters.EligibilityPresenter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A aplicação vem da chave, pelo resolver: nunca da rota nem do corpo (FR-002).
@RestController
@RequestMapping("/collect")
public class CollectEligibilityController implements CollectEligibilitySwagger {

  private final FindEligibleSurveyUseCase findEligibleSurvey;

  CollectEligibilityController(FindEligibleSurveyUseCase findEligibleSurvey) {
    this.findEligibleSurvey = findEligibleSurvey;
  }

  @Override
  @PostMapping("/eligibility")
  public EligibilityResponseDTO check(
      @Valid @RequestBody EligibilityRequestDTO request, AuthenticatedApplication application) {
    return EligibilityPresenter.present(
        findEligibleSurvey.execute(request.toInput(application.applicationId())));
  }
}
