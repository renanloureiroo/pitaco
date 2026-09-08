package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.CheckSurveyPublicationUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.CreateSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DiscardSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListSurveysUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.PublishSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.RenameSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ListSurveysQueryDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationImpedimentsResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublishSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.RenameSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.GetSurveyPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.ListSurveysPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.PublicationImpedimentsPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.SurveyPresenter;
import com.renanloureiroo.pitaco.modules.survey.infra.http.presenters.SurveyVersionPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/applications/{applicationId}/surveys")
public class SurveyController implements SurveyControllerSwagger {

  private final CreateSurveyUseCase createSurveyUseCase;
  private final ListSurveysUseCase listSurveysUseCase;
  private final GetSurveyUseCase getSurveyUseCase;
  private final RenameSurveyUseCase renameSurveyUseCase;
  private final DiscardSurveyUseCase discardSurveyUseCase;
  private final CheckSurveyPublicationUseCase checkSurveyPublicationUseCase;
  private final PublishSurveyUseCase publishSurveyUseCase;

  SurveyController(
      CreateSurveyUseCase createSurveyUseCase,
      ListSurveysUseCase listSurveysUseCase,
      GetSurveyUseCase getSurveyUseCase,
      RenameSurveyUseCase renameSurveyUseCase,
      DiscardSurveyUseCase discardSurveyUseCase,
      CheckSurveyPublicationUseCase checkSurveyPublicationUseCase,
      PublishSurveyUseCase publishSurveyUseCase) {
    this.createSurveyUseCase = createSurveyUseCase;
    this.listSurveysUseCase = listSurveysUseCase;
    this.getSurveyUseCase = getSurveyUseCase;
    this.renameSurveyUseCase = renameSurveyUseCase;
    this.discardSurveyUseCase = discardSurveyUseCase;
    this.checkSurveyPublicationUseCase = checkSurveyPublicationUseCase;
    this.publishSurveyUseCase = publishSurveyUseCase;
  }

  @Override
  @PostMapping
  public ResponseEntity<SurveyResponseDTO> create(
      @PathVariable String applicationId, @Valid @RequestBody CreateSurveyRequestDTO request) {
    var output = createSurveyUseCase.execute(request.toInput(applicationId));

    return ResponseEntity.created(locationOf(output.id())).body(SurveyPresenter.present(output));
  }

  // O schema da página é genérico e vem do tipo de retorno; produces é o que fixa o media type,
  // já que @Schema(implementation) não expressa PageResponseDTO<SurveyResponseDTO>.
  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<SurveyResponseDTO>> list(
      @PathVariable String applicationId, @Valid @ModelAttribute ListSurveysQueryDTO query) {
    var output = listSurveysUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(ListSurveysPresenter.present(output));
  }

  @Override
  @GetMapping("/{surveyId}")
  public ResponseEntity<SurveyDetailResponseDTO> get(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output = getSurveyUseCase.execute(new GetSurveyUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(GetSurveyPresenter.present(output));
  }

  @Override
  @PatchMapping("/{surveyId}")
  public ResponseEntity<SurveyResponseDTO> rename(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @RequestBody RenameSurveyRequestDTO request) {
    var output = renameSurveyUseCase.execute(request.toInput(applicationId, surveyId));

    return ResponseEntity.ok(SurveyPresenter.present(output));
  }

  @Override
  @DeleteMapping("/{surveyId}")
  public ResponseEntity<Void> discard(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    discardSurveyUseCase.execute(new DiscardSurveyUseCase.Input(applicationId, surveyId));

    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/{surveyId}/publication-impediments")
  public ResponseEntity<PublicationImpedimentsResponseDTO> checkPublication(
      @PathVariable String applicationId, @PathVariable String surveyId) {
    var output =
        checkSurveyPublicationUseCase.execute(
            new CheckSurveyPublicationUseCase.Input(applicationId, surveyId));

    return ResponseEntity.ok(PublicationImpedimentsPresenter.present(output));
  }

  @Override
  @PostMapping("/{surveyId}/publication")
  public ResponseEntity<SurveyVersionResponseDTO> publish(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @RequestBody(required = false) PublishSurveyRequestDTO request) {
    var body = request == null ? PublishSurveyRequestDTO.empty() : request;
    var output = publishSurveyUseCase.execute(body.toInput(applicationId, surveyId));

    var location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/applications/{applicationId}/surveys/{surveyId}/versions/{number}")
            .buildAndExpand(applicationId, surveyId, output.number())
            .toUri();

    return ResponseEntity.created(location).body(SurveyVersionPresenter.present(output));
  }

  private static java.net.URI locationOf(String surveyId) {
    return ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(surveyId)
        .toUri();
  }
}
