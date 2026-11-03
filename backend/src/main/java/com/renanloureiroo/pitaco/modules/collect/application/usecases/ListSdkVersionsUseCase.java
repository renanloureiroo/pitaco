package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SdkVersionUsageOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository.SdkVersionUsage;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// A proporção é do tráfego recente, não do acumulado: uma versão que foi a maioria no ano passado
// não pode continuar parecendo relevante porque somou muito antes de sumir.
@Slf4j
public class ListSdkVersionsUseCase
    implements UseCase<ListSdkVersionsUseCase.Input, ListSdkVersionsUseCase.Output> {

  private final ApplicationScopeGateway applications;
  private final SdkVersionUsageRepository usage;
  private final Duration recentWindow;
  private final Duration staleAfter;

  public ListSdkVersionsUseCase(
      ApplicationScopeGateway applications,
      SdkVersionUsageRepository usage,
      Duration recentWindow,
      Duration staleAfter) {
    this.applications = applications;
    this.usage = usage;
    this.recentWindow = recentWindow;
    this.staleAfter = staleAfter;
  }

  public record Input(String applicationId) {}

  public record Output(
      LocalDate recentFrom, long recentRequests, List<SdkVersionUsageOutput> versions) {}

  @Override
  public Output execute(Input input) {
    var applicationId = CollectScope.existingApplicationIdOf(applications, input.applicationId());

    var now = Instant.now();
    var recentFrom = LocalDate.ofInstant(now.minus(recentWindow), ZoneOffset.UTC);
    var staleBefore = now.minus(staleAfter);

    var rows = usage.findByApplication(applicationId, recentFrom);
    var recentTotal = rows.stream().mapToLong(SdkVersionUsage::recentRequestCount).sum();

    var versions =
        rows.stream()
            .sorted(Comparator.comparing(SdkVersionUsage::version).reversed())
            .map(row -> outputOf(row, recentTotal, staleBefore))
            .toList();

    log.info(
        "Versões do SDK listadas application={} versoes={}", applicationId.value(), versions.size());

    return new Output(recentFrom, recentTotal, versions);
  }

  private static SdkVersionUsageOutput outputOf(
      SdkVersionUsage row, long recentTotal, Instant staleBefore) {
    return new SdkVersionUsageOutput(
        row.version().value(),
        row.requestCount(),
        row.recentRequestCount(),
        recentTotal == 0
            ? Optional.empty()
            : Optional.of((double) row.recentRequestCount() / recentTotal),
        row.firstSeenAt(),
        row.lastSeenAt(),
        row.lastSeenAt().isBefore(staleBefore));
  }
}
