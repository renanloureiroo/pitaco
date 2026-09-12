package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.ReportSdkErrorUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Schema(
    description =
        """
        Falha interna do SDK. O relatório descreve o SDK, nunca o usuário: não envie dado \
        pessoal, identificação do respondente, atributo de segmentação nem conteúdo de \
        resposta. Chaves de contexto com palavras de dado pessoal (email, user, device, \
        answer, text, value, token…) e valores com cara de e-mail ou de número longo são \
        descartados no servidor.""")
public record SdkErrorReportRequestDTO(
    @Size(max = 40, message = "Tipo não pode passar de 40 caracteres")
        @Schema(
            description = "Tipo da falha; fora da lista vira unknown",
            allowableValues = {
              "render_error",
              "network_error",
              "malformed_response",
              "storage_error",
              "unknown"
            },
            example = "render_error")
        String kind,
    @Size(max = 4000, message = "Mensagem não pode passar de 4000 caracteres")
        @Schema(
            description = "Mensagem da falha; guardada com até 500 caracteres",
            example = "Tipo de pergunta sem renderizador: matrix")
        String message,
    @Size(max = 50, message = "No máximo 50 chaves de contexto")
        @Schema(
            description =
                "Estado do SDK relevante para investigar. Aceita texto, número, booleano e um "
                    + "nível de objeto; guarda até 20 chaves e cerca de 2 KB",
            example = "{\"questionType\": \"matrix\", \"stage\": \"render\"}")
        Map<String, Object> context,
    @Schema(
            description =
                "Quando a falha aconteceu no dispositivo. Ausente, ou no futuro, vale o "
                    + "instante do recebimento",
            example = "2026-09-12T13:45:00Z")
        Instant occurredAt) {

  public ReportSdkErrorUseCase.Input toInput(String applicationId, String sdkVersion) {
    return new ReportSdkErrorUseCase.Input(
        applicationId,
        Optional.ofNullable(sdkVersion),
        Optional.ofNullable(kind),
        Optional.ofNullable(message),
        context == null ? Map.of() : context,
        Optional.ofNullable(occurredAt));
  }
}
