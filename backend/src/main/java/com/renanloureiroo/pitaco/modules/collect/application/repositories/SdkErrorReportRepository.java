package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import java.time.Instant;
import java.util.Optional;

public interface SdkErrorReportRepository {

  SdkErrorReport create(SdkErrorReport report);

  // items do recebido mais recentemente para o mais antigo, desempate por id desc; filtro ausente
  // não restringe.
  Page<SdkErrorReport> findPage(ListSdkErrorsQuery query);

  int deleteReceivedBefore(Instant threshold);

  record ListSdkErrorsQuery(
      ApplicationId applicationId,
      Optional<SdkErrorKind> kind,
      Optional<String> sdkVersion,
      int page,
      int size)
      implements PageQuery {}
}
