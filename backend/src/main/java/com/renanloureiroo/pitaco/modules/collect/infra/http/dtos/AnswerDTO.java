package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.AnswerDraft;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.RawAnswerValue;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@Schema(description = "Uma resposta do envio")
public record AnswerDTO(
    @NotBlank(message = "Chave da pergunta é obrigatória")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String questionKey,
    @NotNull(message = "Situação da resposta é obrigatória")
        @Schema(
            description = "ANSWERED traz valor; SKIPPED não traz nenhum",
            requiredMode = Schema.RequiredMode.REQUIRED)
        AnswerStatus status,
    @Schema(
            description =
                "String na escolha única e no texto livre, inteiro nos tipos numéricos, "
                    + "array de strings na escolha múltipla, ausente quando pulada",
            example = "bom",
            oneOf = {String.class, Integer.class, String[].class},
            nullable = true)
        Object value) {

  // A forma tem de ser uma das quatro aceitas. Um objeto ou um booleano aqui é corpo malformado
  // para este contrato, e não problema de conteúdo — por isso recusa 400, não 422.
  @AssertTrue(message = "Valor deve ser texto, inteiro ou lista de textos")
  @Schema(hidden = true)
  public boolean isValueShapeSupported() {
    return switch (value) {
      case null -> true;
      case String ignored -> true;
      case Integer ignored -> true;
      case Long ignored -> true;
      case List<?> options -> options.stream().allMatch(String.class::isInstance);
      default -> false;
    };
  }

  public AnswerDraft toDraft() {
    return new AnswerDraft(QuestionKey.of(questionKey), status, rawValue());
  }

  private Optional<RawAnswerValue> rawValue() {
    return switch (value) {
      case null -> Optional.empty();
      case String text -> Optional.of(new RawAnswerValue.RawText(text));
      case Integer number -> Optional.of(new RawAnswerValue.RawNumber(number));
      case Long number -> Optional.of(new RawAnswerValue.RawNumber(number.intValue()));
      case List<?> options ->
          Optional.of(
              new RawAnswerValue.RawChoices(options.stream().map(String::valueOf).toList()));
      default -> Optional.empty();
    };
  }
}
