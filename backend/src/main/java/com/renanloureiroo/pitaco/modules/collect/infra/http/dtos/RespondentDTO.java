package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;

// Nada aqui identifica pessoa: os dois campos são referências opacas, e nenhum vai para log
// (FR-008, FR-039).
@Schema(description = "Como o respondente é reconhecido nesta aplicação")
public record RespondentDTO(
    @Size(max = 200, message = "Identificação não pode passar de 200 caracteres")
        @Schema(
            description =
                "Referência opaca do app hospedeiro. Prevalece sobre o dispositivo quando "
                    + "presente",
            example = "u-8f1c",
            maxLength = 200,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String reference,
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Identificador de dispositivo deve ser um UUID")
        @Schema(
            description =
                "UUID gerado pelo SDK. Obrigatório na ausência da referência do app",
            example = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String deviceId) {

  // Espelha RespondentIdentity.of: a referência do app prevalece, e sem nenhuma das duas não há
  // respondente. Aqui a recusa é de constraint, e o contrato desta borda é `request.invalid`.
  @AssertTrue(message = "Informe a referência do app ou o identificador do dispositivo")
  @Schema(hidden = true)
  public boolean isIdentified() {
    return referenceOrEmpty().filter(value -> !value.isBlank()).isPresent()
        || deviceIdOrEmpty().filter(value -> !value.isBlank()).isPresent();
  }

  public Optional<String> referenceOrEmpty() {
    return Optional.ofNullable(reference);
  }

  public Optional<String> deviceIdOrEmpty() {
    return Optional.ofNullable(deviceId);
  }
}
