package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "O que convém saber antes de publicar: regras que não alcançam ninguém e pesquisas que "
            + "disputam o mesmo evento. Nenhum aviso impede a publicação")
public record PublicationWarningsResponseDTO(
    @Schema(description = "Avisos; vazio quando não há nada a observar",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<PublicationWarningDTO> warnings) {}
