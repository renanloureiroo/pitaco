package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Uma exibição com seu instantâneo de atributos e suas respostas")
public record DisplayDetailResponseDTO(
    @Schema(
            description = "Identificador da exibição",
            example = "d1f0a3c8-77b2-4e19-9a5c-0b3e6f8d2a41",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Respondente a quem a pesquisa foi exibida",
            example = "b5c9e740-1a83-4f2d-9e06-7c4b1a8f3d25",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String respondentId,
    @Schema(
            description = "Pesquisa exibida",
            example = "6e1d9b30-4c72-4a85-b3f1-9d0a2c8e5f47",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String surveyId,
    @Schema(
            description = "Versão da pesquisa que foi exibida",
            example = "8c2b5e14-3a97-4d60-b1f8-5e7c9a0d4b62",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String versionId,
    @Schema(
            description = "Número da versão exibida",
            example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNumber,
    @Schema(
            description = "Grupo de comparabilidade da versão exibida",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int comparabilityGroup,
    @Schema(
            description = "Desfecho gravado da exibição",
            allowableValues = {"STARTED", "COMPLETED", "DISMISSED"},
            example = "COMPLETED",
            requiredMode = Schema.RequiredMode.REQUIRED)
        DisplayOutcome outcome,
    @Schema(
            description = "Versão do SDK que exibiu. Ausente quando não informada",
            example = "1.4.0",
            nullable = true)
        String sdkVersion,
    @Schema(
            description = "Instante da abertura, em UTC",
            example = "2026-09-08T14:22:31Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant openedAt,
    @Schema(
            description = "Instante do fechamento. Presente se e somente se o desfecho é final",
            example = "2026-09-08T14:23:07Z",
            nullable = true)
        Instant closedAt,
    @Schema(
            description =
                "Instantâneo dos atributos informados na consulta de elegibilidade. Pertence à "
                    + "exibição, não ao respondente",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, String> attributes,
    @Schema(
            description =
                "Respostas na ordem das perguntas da versão exibida. Vazio na exibição ainda "
                    + "aberta e na dispensada",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<AnswerReadResponseDTO> answers) {}
