package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.GetApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApplicationsUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationSummaryResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationRequestDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ListApplicationsQueryDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.presenters.CreateApplicationPresenter;
import com.renanloureiroo.pitaco.modules.app.infra.http.presenters.GetApplicationPresenter;
import com.renanloureiroo.pitaco.modules.app.infra.http.presenters.ListApplicationsPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/applications")
public class ApplicationController implements ApplicationControllerSwagger {

  private final CreateApplicationUseCase createAppUseCase;
  private final ListApplicationsUseCase listApplicationsUseCase;
  private final GetApplicationUseCase getApplicationUseCase;

  ApplicationController(
      CreateApplicationUseCase createAppUseCase,
      ListApplicationsUseCase listApplicationsUseCase,
      GetApplicationUseCase getApplicationUseCase) {
    this.createAppUseCase = createAppUseCase;
    this.listApplicationsUseCase = listApplicationsUseCase;
    this.getApplicationUseCase = getApplicationUseCase;
  }

  @Override
  @PostMapping
  public ResponseEntity<CreateApplicationResponseDTO> create(
      @Valid @RequestBody CreateApplicationRequestDTO request) {
    var output = createAppUseCase.execute(request.toInput());
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(output.id())
            .toUri();

    return ResponseEntity.created(location).body(CreateApplicationPresenter.present(output));
  }

  // O schema da página é genérico e vem do tipo de retorno; produces é o que fixa o media type,
  // já que @Schema(implementation) não expressa PageResponseDTO<ApplicationSummaryResponseDTO>.
  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<ApplicationSummaryResponseDTO>> list(
      @Valid @ModelAttribute ListApplicationsQueryDTO query) {
    var output = listApplicationsUseCase.execute(query.toInput());

    return ResponseEntity.ok(ListApplicationsPresenter.present(output));
  }

  @Override
  @GetMapping("/{applicationId}")
  public ResponseEntity<ApplicationResponseDTO> get(@PathVariable String applicationId) {
    var output = getApplicationUseCase.execute(new GetApplicationUseCase.Input(applicationId));

    return ResponseEntity.ok(GetApplicationPresenter.present(output));
  }
}
