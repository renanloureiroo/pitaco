package com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.privacy.application.usecases.DeleteRespondentUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.Optional;

@Schema(description = "A identidade do respondente a excluir: a referência do app ou o dispositivo")
public record EraseRespondentQueryDTO(
    @Size(max = 200, message = "Identificação do respondente não pode passar de 200 caracteres")
        @Schema(
            description = "Referência opaca do usuário, como o app hospedeiro a enviou ao SDK",
            example = "u-8f1c")
        String reference,
    @Size(max = 200, message = "Identificação do respondente não pode passar de 200 caracteres")
        @Schema(
            description = "Identificador do dispositivo, para respondente que nunca teve referência",
            example = "device-3a9e")
        String deviceId) {

  @AssertTrue(
      message = "É preciso informar a referência do app ou o identificador do dispositivo")
  @Schema(hidden = true)
  public boolean isIdentified() {
    return present(reference) || present(deviceId);
  }

  @AssertTrue(
      message = "Informe a referência do app ou o identificador do dispositivo, não os dois")
  @Schema(hidden = true)
  public boolean isUnambiguous() {
    return !(present(reference) && present(deviceId));
  }

  public DeleteRespondentUseCase.Input toInput(String applicationId) {
    return new DeleteRespondentUseCase.Input(
        applicationId, Optional.ofNullable(reference), Optional.ofNullable(deviceId));
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
