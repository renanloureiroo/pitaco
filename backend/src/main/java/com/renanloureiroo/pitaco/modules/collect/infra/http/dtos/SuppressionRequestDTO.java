package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.RecordSuppressionUseCase;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Schema(
    description =
        "Uma pesquisa elegível que o SDK não soube renderizar. Nada foi exibido e nenhuma "
            + "exibição foi aberta")
public record SuppressionRequestDTO(
    @NotBlank(message = "Identificador de pesquisa é obrigatório")
        @Size(max = 64, message = "Identificador de pesquisa não pode passar de 64 caracteres")
        @Schema(
            description = "A pesquisa recebida na consulta de elegibilidade",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String surveyId,
    @NotBlank(message = "Identificador de versão é obrigatório")
        @Size(max = 64, message = "Identificador de versão não pode passar de 64 caracteres")
        @Schema(
            description = "A versão recebida na consulta de elegibilidade",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String versionId,
    @NotBlank(message = "Motivo da supressão é obrigatório")
        @Pattern(
            regexp = "^(unknown_question_type|unsupported_feature)$",
            message = "Motivo deve ser unknown_question_type ou unsupported_feature")
        @Schema(
            description = "Por que o SDK não renderizou",
            allowableValues = {"unknown_question_type", "unsupported_feature"},
            example = "unknown_question_type",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
    @Size(max = 20, message = "No máximo 20 tipos de pergunta")
        @Schema(
            description = "Tipos de pergunta que o SDK não reconheceu, para diagnóstico",
            example = "[\"matrix\"]")
        List<@Size(max = 40, message = "Tipo não pode passar de 40 caracteres") String>
            questionTypes,
    @Size(max = 20, message = "No máximo 20 recursos")
        @Schema(
            description = "Recursos que o SDK não suporta, para diagnóstico",
            example = "[\"conditional_display\"]")
        List<@Size(max = 40, message = "Recurso não pode passar de 40 caracteres") String>
            features,
    @Size(max = 200, message = "Identificação não pode passar de 200 caracteres")
        @Schema(
            description =
                "Referência opaca do app hospedeiro, como na consulta de elegibilidade. Não é "
                    + "gravada: serve só para não contar duas vezes a mesma supressão",
            example = "u-8f1c",
            maxLength = 200)
        String respondentReference,
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Identificador de dispositivo deve ser um UUID")
        @Schema(
            description = "UUID gerado pelo SDK, como na consulta de elegibilidade",
            example = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11")
        String deviceId) {

  public RecordSuppressionUseCase.Input toInput(String applicationId, String sdkVersion) {
    return new RecordSuppressionUseCase.Input(
        applicationId,
        surveyId,
        versionId,
        SuppressionReason.valueOf(reason.toUpperCase(Locale.ROOT)),
        Optional.ofNullable(respondentReference),
        Optional.ofNullable(deviceId),
        Optional.ofNullable(sdkVersion));
  }
}
