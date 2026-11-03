package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "O que ainda falta para a pesquisa poder ser publicada. É a mesma lista que a publicação "
            + "usaria para recusar, e vem sempre completa")
public record PublicationImpedimentsResponseDTO(
    @Schema(
            description = "Lista completa de pendências; vazia quando o rascunho está publicável",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<PublicationImpedimentDTO> impediments) {}
