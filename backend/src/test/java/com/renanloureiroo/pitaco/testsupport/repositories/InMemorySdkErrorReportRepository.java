package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkErrorReportRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemorySdkErrorReportRepository implements SdkErrorReportRepository {

  private final List<SdkErrorReport> reports = new ArrayList<>();

  @Override
  public SdkErrorReport create(SdkErrorReport report) {
    reports.add(report);
    return report;
  }

  @Override
  public Page<SdkErrorReport> findPage(ListSdkErrorsQuery query) {
    var matching =
        reports.stream()
            .filter(report -> report.getApplicationId().equals(query.applicationId()))
            .filter(report -> query.kind().map(kind -> report.getKind() == kind).orElse(true))
            .filter(
                report ->
                    query
                        .sdkVersion()
                        .map(
                            version ->
                                report.sdkVersion().map(SdkVersion::value).orElse("").equals(version))
                        .orElse(true))
            .sorted(
                Comparator.comparing(SdkErrorReport::getReceivedAt)
                    .thenComparing(report -> report.id().value())
                    .reversed())
            .toList();

    return new Page<>(
        matching.stream().skip(query.offset()).limit(query.size()).toList(), matching.size());
  }

  @Override
  public int deleteReceivedBefore(Instant threshold) {
    var before = reports.size();
    reports.removeIf(report -> report.getReceivedAt().isBefore(threshold));
    return before - reports.size();
  }

  public InMemorySdkErrorReportRepository with(SdkErrorReport report) {
    reports.add(report);
    return this;
  }

  public List<SdkErrorReport> findAll() {
    return List.copyOf(reports);
  }
}
