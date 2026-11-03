package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.RecordSuppressionUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ReportSdkErrorUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkErrorReportRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SuppressionRequestDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/collect")
public class CollectHealthController implements CollectHealthSwagger {

  private final RecordSuppressionUseCase recordSuppression;
  private final ReportSdkErrorUseCase reportSdkError;

  CollectHealthController(
      RecordSuppressionUseCase recordSuppression, ReportSdkErrorUseCase reportSdkError) {
    this.recordSuppression = recordSuppression;
    this.reportSdkError = reportSdkError;
  }

  @Override
  @PostMapping("/suppressions")
  public ResponseEntity<Void> suppress(
      @Valid @RequestBody SuppressionRequestDTO request,
      @RequestHeader(value = SdkVersionHeader.NAME, required = false) String sdkVersion,
      AuthenticatedApplication application) {
    recordSuppression.execute(request.toInput(application.applicationId(), sdkVersion));
    return ResponseEntity.accepted().build();
  }

  @Override
  @PostMapping("/sdk-errors")
  public ResponseEntity<Void> report(
      @Valid @RequestBody SdkErrorReportRequestDTO request,
      @RequestHeader(value = SdkVersionHeader.NAME, required = false) String sdkVersion,
      AuthenticatedApplication application) {
    reportSdkError.execute(request.toInput(application.applicationId(), sdkVersion));
    return ResponseEntity.accepted().build();
  }
}
