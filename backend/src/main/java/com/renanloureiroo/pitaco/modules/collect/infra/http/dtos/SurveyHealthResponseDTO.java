package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    description =
        "Saúde de uma pesquisa: o que separa \"está sendo suprimida\" de \"o evento nunca "
            + "chegou\", as duas causas de zero respostas que pedem correções opostas")
public record SurveyHealthResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant from,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant to,
    @Schema(
            description = "Exibições abertas no período, qualquer desfecho",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long displays,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Suppressions suppressions,
    @Schema(
            description =
                "Supressões sobre exibições mais supressões, de 0 a 1; ausente sem nenhuma "
                    + "das duas",
            example = "0.12",
            nullable = true)
        Double suppressionShare,
    @Schema(
            description =
                "A proporção passou do limiar e o total passou do mínimo: vale avisar",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean relevant,
    @Schema(
            description =
                "Versão mínima do SDK que renderiza a versão publicada; ausente se nunca "
                    + "publicou",
            example = "1.0.0",
            nullable = true)
        String minRequiredVersion,
    @Schema(
            description = "Evento que a versão publicada escuta",
            example = "checkout.completed",
            nullable = true)
        String eventName,
    @Schema(
            description =
                "Última vez que o evento chegou desta aplicação; ausente quando nunca chegou",
            nullable = true)
        Instant eventLastSeenAt) {

  @Schema(name = "SurveySuppressions", description = "Supressões no período")
  public record Suppressions(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
      @Schema(description = "Contagem por motivo, os dois sempre presentes",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<ReasonCount> byReason,
      @Schema(description = "Contagem por versão do SDK, da mais nova para a mais antiga",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<VersionCount> bySdkVersion) {}

  @Schema(name = "SuppressionReasonCount")
  public record ReasonCount(
      @Schema(
              allowableValues = {"unknown_question_type", "unsupported_feature"},
              requiredMode = Schema.RequiredMode.REQUIRED)
          String reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {}

  @Schema(name = "SuppressionVersionCount")
  public record VersionCount(
      @Schema(description = "Ausente quando o SDK não informou a versão", nullable = true)
          String sdkVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {}
}
