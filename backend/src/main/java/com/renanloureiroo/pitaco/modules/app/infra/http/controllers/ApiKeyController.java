package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.application.usecases.GetApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.IssueApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApiKeysUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.RevokeApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApiKeyResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyRequestDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ListApiKeysQueryDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.presenters.GetApiKeyPresenter;
import com.renanloureiroo.pitaco.modules.app.infra.http.presenters.IssueApiKeyPresenter;
import com.renanloureiroo.pitaco.modules.app.infra.http.presenters.ListApiKeysPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/applications/{applicationId}/api-keys")
public class ApiKeyController implements ApiKeyControllerSwagger {

  private final IssueApiKeyUseCase issueApiKeyUseCase;
  private final RevokeApiKeyUseCase revokeApiKeyUseCase;
  private final ListApiKeysUseCase listApiKeysUseCase;
  private final GetApiKeyUseCase getApiKeyUseCase;

  ApiKeyController(
      IssueApiKeyUseCase issueApiKeyUseCase,
      RevokeApiKeyUseCase revokeApiKeyUseCase,
      ListApiKeysUseCase listApiKeysUseCase,
      GetApiKeyUseCase getApiKeyUseCase) {
    this.issueApiKeyUseCase = issueApiKeyUseCase;
    this.revokeApiKeyUseCase = revokeApiKeyUseCase;
    this.listApiKeysUseCase = listApiKeysUseCase;
    this.getApiKeyUseCase = getApiKeyUseCase;
  }

  // O schema da página é genérico e vem do tipo de retorno; produces é o que fixa o media type,
  // já que @Schema(implementation) não expressa PageResponseDTO<ApiKeyResponseDTO>.
  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<ApiKeyResponseDTO>> list(
      @PathVariable String applicationId, @Valid @ModelAttribute ListApiKeysQueryDTO query) {
    var output = listApiKeysUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(ListApiKeysPresenter.present(output));
  }

  @Override
  @GetMapping("/{apiKeyId}")
  public ResponseEntity<ApiKeyResponseDTO> get(
      @PathVariable String applicationId, @PathVariable String apiKeyId) {
    var output = getApiKeyUseCase.execute(new GetApiKeyUseCase.Input(applicationId, apiKeyId));

    return ResponseEntity.ok(GetApiKeyPresenter.present(output));
  }

  @Override
  @PostMapping
  public ResponseEntity<IssueApiKeyResponseDTO> issue(
      @PathVariable String applicationId, @Valid @RequestBody IssueApiKeyRequestDTO request) {
    var output = issueApiKeyUseCase.execute(request.toInput(applicationId));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(output.id())
            .toUri();

    return ResponseEntity.created(location).body(IssueApiKeyPresenter.present(output));
  }

  @Override
  @DeleteMapping("/{apiKeyId}")
  public ResponseEntity<Void> revoke(
      @PathVariable String applicationId, @PathVariable String apiKeyId) {
    revokeApiKeyUseCase.execute(new RevokeApiKeyUseCase.Input(applicationId, apiKeyId));

    return ResponseEntity.noContent().build();
  }
}
