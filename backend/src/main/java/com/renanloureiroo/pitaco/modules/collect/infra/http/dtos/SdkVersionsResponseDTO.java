package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Distribuição de versões do SDK que falaram com a aplicação")
public record SdkVersionsResponseDTO(
    @Schema(
            description = "Primeiro dia, em UTC, da janela recente que dá a proporção",
            example = "2026-08-29",
            requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate recentFrom,
    @Schema(
            description = "Consultas de elegibilidade na janela recente, somando todas as versões",
            example = "12840",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long recentRequests,
    @Schema(
            description = "Versões já vistas, da mais nova para a mais antiga",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<Version> versions) {

  @Schema(name = "SdkVersionUsage", description = "Uma versão do SDK vista na aplicação")
  public record Version(
      @Schema(example = "1.4.2", requiredMode = Schema.RequiredMode.REQUIRED) String version,
      @Schema(
              description = "Consultas desde a primeira vez vista; aproximado",
              example = "53210",
              requiredMode = Schema.RequiredMode.REQUIRED)
          long requestCount,
      @Schema(
              description = "Consultas na janela recente",
              example = "10400",
              requiredMode = Schema.RequiredMode.REQUIRED)
          long recentRequestCount,
      @Schema(
              description =
                  "Proporção das consultas recentes, de 0 a 1; ausente quando não houve "
                      + "consulta na janela",
              example = "0.81",
              nullable = true)
          Double recentShare,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant firstSeenAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant lastSeenAt,
      @Schema(
              description = "Não aparece no tráfego há mais tempo que o limite configurado",
              requiredMode = Schema.RequiredMode.REQUIRED)
          boolean stale) {}
}
