package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Quais versões são comparáveis entre si. Versões separadas apenas por mudanças "
            + "cosméticas formam um grupo; uma mudança semântica abre um grupo novo")
public record VersionComparabilityResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Group> groups) {

  @Schema(description = "Um grupo de versões com respostas somáveis entre si")
  public record Group(
      @Schema(
              description = "Identificador do grupo",
              example = "1",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int group,
      @Schema(
              description = "Números das versões do grupo",
              example = "[1, 2]",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<Integer> versions) {}
}
