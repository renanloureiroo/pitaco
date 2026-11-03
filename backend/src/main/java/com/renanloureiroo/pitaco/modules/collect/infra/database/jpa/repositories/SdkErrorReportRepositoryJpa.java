package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkErrorReportRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorContext;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReportId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SdkErrorReportRepositoryJpa implements SdkErrorReportRepository {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<LinkedHashMap<String, Object>> CONTEXT_TYPE =
      new TypeReference<>() {};

  private final SdkErrorReportJpaRepository repository;

  public SdkErrorReportRepositoryJpa(SdkErrorReportJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public SdkErrorReport create(SdkErrorReport report) {
    repository.insert(
        report.id().value(),
        report.getApplicationId().value(),
        report.sdkVersion().map(SdkVersion::value).orElse(null),
        report.getKind().name(),
        report.getMessage(),
        JSON.writeValueAsString(report.getContext().values()),
        report.getOccurredAt(),
        report.getReceivedAt());
    return report;
  }

  @Override
  public Page<SdkErrorReport> findPage(ListSdkErrorsQuery query) {
    var kind = query.kind().map(SdkErrorKind::name).orElse(null);
    var version = query.sdkVersion().orElse(null);
    var applicationId = query.applicationId().value();

    var items =
        repository
            .findPage(applicationId, kind, version, query.size(), query.offset())
            .stream()
            .map(row -> toDomain(query, row))
            .toList();

    return new Page<>(items, repository.countPage(applicationId, kind, version));
  }

  @Override
  public int deleteReceivedBefore(Instant threshold) {
    return repository.deleteReceivedBefore(threshold);
  }

  private static SdkErrorReport toDomain(ListSdkErrorsQuery query, Object[] row) {
    return SdkErrorReport.restore(
        SdkErrorReportId.of((String) row[0]),
        query.applicationId(),
        Optional.ofNullable((String) row[1]).flatMap(SdkVersion::parse),
        SdkErrorKind.valueOf((String) row[2]),
        (String) row[3],
        new SdkErrorContext(contextOf((String) row[4]), 0),
        NativeValues.instant(row[5]),
        NativeValues.instant(row[6]));
  }

  private static Map<String, Object> contextOf(String json) {
    return json == null || json.isBlank() ? Map.of() : JSON.readValue(json, CONTEXT_TYPE);
  }
}
