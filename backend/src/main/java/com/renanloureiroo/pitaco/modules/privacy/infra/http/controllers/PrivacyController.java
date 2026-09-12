package com.renanloureiroo.pitaco.modules.privacy.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.DeleteRespondentUseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.GetRetentionPreviewUseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ListDeletionAuditsUseCase;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.DeletionAuditResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.EraseRespondentQueryDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.ListDeletionAuditsQueryDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RespondentErasureResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RetentionPreviewResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.presenters.PrivacyPresenter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationId}")
public class PrivacyController implements PrivacySwagger {

  private final DeleteRespondentUseCase deleteRespondentUseCase;
  private final ListDeletionAuditsUseCase listDeletionAuditsUseCase;
  private final GetRetentionPreviewUseCase getRetentionPreviewUseCase;

  PrivacyController(
      DeleteRespondentUseCase deleteRespondentUseCase,
      ListDeletionAuditsUseCase listDeletionAuditsUseCase,
      GetRetentionPreviewUseCase getRetentionPreviewUseCase) {
    this.deleteRespondentUseCase = deleteRespondentUseCase;
    this.listDeletionAuditsUseCase = listDeletionAuditsUseCase;
    this.getRetentionPreviewUseCase = getRetentionPreviewUseCase;
  }

  // Pela identidade opaca na query, e não pelo identificador interno: é a identidade que chega
  // no pedido de exclusão, vinda do app hospedeiro.
  @Override
  @DeleteMapping(value = "/respondents", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RespondentErasureResponseDTO> erase(
      @PathVariable String applicationId, @Valid @ModelAttribute EraseRespondentQueryDTO query) {
    var output = deleteRespondentUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(PrivacyPresenter.present(output));
  }

  @Override
  @GetMapping(value = "/deletion-audits", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<DeletionAuditResponseDTO>> audits(
      @PathVariable String applicationId,
      @Valid @ModelAttribute ListDeletionAuditsQueryDTO query) {
    var output = listDeletionAuditsUseCase.execute(query.toInput(applicationId));

    return ResponseEntity.ok(PrivacyPresenter.present(output));
  }

  @Override
  @GetMapping(value = "/retention-preview", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RetentionPreviewResponseDTO> retentionPreview(
      @PathVariable String applicationId) {
    var output =
        getRetentionPreviewUseCase.execute(new GetRetentionPreviewUseCase.Input(applicationId));

    return ResponseEntity.ok(PrivacyPresenter.present(output));
  }
}
