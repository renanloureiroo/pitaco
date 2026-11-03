package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Um atributo que o app desta aplicação já enviou, com os valores vistos")
public record ObservedAttributeResponseDTO(
    @Schema(
            description = "Nome do atributo, exatamente como o app o envia",
            example = "plano",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "Instante em que o atributo foi visto pela primeira vez, em UTC",
            example = "2026-08-30T09:11:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant firstSeenAt,
    @Schema(
            description =
                "Instante em que foi visto pela última vez, em UTC, com precisão de alguns minutos",
            example = "2026-09-08T14:22:31Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant lastSeenAt,
    @Schema(
            description =
                "Valores já vistos, em ordem alfabética. Param de acumular em 200 valores "
                    + "distintos por atributo",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<Value> values) {

  @Schema(description = "Um valor que o atributo já assumiu")
  public record Value(
      @Schema(example = "premium", requiredMode = Schema.RequiredMode.REQUIRED) String value,
      @Schema(example = "2026-09-08T14:22:31Z", requiredMode = Schema.RequiredMode.REQUIRED)
          Instant lastSeenAt) {}
}
