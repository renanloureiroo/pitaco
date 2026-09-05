package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationRequestDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController()
@RequestMapping("/applications")
public class ApplicationController implements ApplicationControllerSwagger {
  private final CreateApplicationUseCase createAppUseCase;

  ApplicationController(CreateApplicationUseCase createAppUseCase) {
    this.createAppUseCase = createAppUseCase;
  }

  @Override
  @PostMapping()
  public ResponseEntity<CreateApplicationResponseDTO> create(
      @Valid @RequestBody CreateApplicationRequestDTO request) {
    var output = createAppUseCase.execute(request.toInput());
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(output.id())
            .toUri();
    return ResponseEntity.created(location).body(CreateApplicationResponseDTO.from(output));
  }
}
