package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "O que aconteceu com cada evento do lote. Serve à depuração do SDK; a fila local remove "
            + "o lote em qualquer 202")
public record InteractionEventsReceiptDTO(
    @Schema(
            description = "Eventos gravados agora",
            example = "12",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int accepted,
    @Schema(
            description =
                "Eventos cujo seq já estava gravado para a exibição, ou repetido no próprio lote: "
                    + "reconhecidos sem gravar de novo",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int duplicated,
    @Schema(description = "Eventos descartados, por motivo", requiredMode = Schema.RequiredMode.REQUIRED)
        DiscardedEventsDTO discarded) {

  @Schema(description = "Contagem de descartes por motivo; zero quando o motivo não ocorreu")
  public record DiscardedEventsDTO(
      @Schema(
              description =
                  "Exibição inexistente, de outra aplicação ou de aplicação inativa, "
                      + "indistinguíveis de propósito",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int displayUnavailable,
      @Schema(
              description = "O lote chegou depois da janela de aceitação contada da abertura",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int outsideWindow,
      @Schema(
              description = "Tipo fora do catálogo que o servidor conhece",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int unknownType,
      @Schema(
              description =
                  "Envelope incompleto ou fora da forma: catalogVersion, seq, occurredAt ou "
                      + "elapsedMs ausentes ou inválidos, displayId de outra exibição, evento de "
                      + "pergunta sem questionKey, ou escolha registrada em pergunta de texto livre",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int invalidEnvelope,
      @Schema(
              description = "questionKey que não pertence à versão exibida",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int unknownQuestion,
      @Schema(
              description = "Além do teto de eventos por exibição",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int overLimit) {}
}
