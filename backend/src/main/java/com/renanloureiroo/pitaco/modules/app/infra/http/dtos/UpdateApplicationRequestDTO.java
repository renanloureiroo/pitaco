package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.app.application.usecases.UpdateApplicationUseCase;
import com.renanloureiroo.pitaco.core.usecase.Patch;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Optional;

// Três estados por campo: ausente chega como referência nula e não mexe; null chega Optional
// vazio e remove; valor chega presente e define. Quem garante a distinção é o deserializer.
@JsonDeserialize(using = UpdateApplicationRequestDeserializer.class)
@Schema(
    description =
        "Campos a alterar numa aplicação. Campo omitido não muda; prazo enviado como null é "
            + "removido. O slug é imutável e não entra aqui")
public record UpdateApplicationRequestDTO(
    @Schema(
            description = "Novo nome de exibição",
            example = "Acme App",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<
                @NotBlank(message = "Nome é obrigatório")
                @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
                String>
            name,
    @Schema(
            description = "Dias de descanso entre pesquisas do mesmo respondente. null remove",
            example = "15",
            minimum = "1",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<@Positive(message = "O intervalo de descanso deve ser de ao menos um dia") Integer>
            quietPeriodDays,
    @Schema(
            description = "Dias até o descarte das respostas. null remove",
            example = "180",
            minimum = "1",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<@Positive(message = "O prazo de retenção deve ser de ao menos um dia") Integer>
            retentionDays,
    @Schema(
            description =
                "Dias até o descarte do texto livre, nunca maior que o prazo geral. null volta a "
                    + "seguir o prazo geral",
            example = "30",
            minimum = "1",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<
                @Positive(
                    message = "O prazo de retenção de texto livre deve ser de ao menos um dia")
                Integer>
            openTextRetentionDays) {

  public UpdateApplicationUseCase.Input toInput(String applicationId) {
    return new UpdateApplicationUseCase.Input(
        applicationId,
        name == null ? Optional.empty() : name,
        patchOf(quietPeriodDays),
        patchOf(retentionDays),
        patchOf(openTextRetentionDays));
  }

  private static <T> Patch<T> patchOf(Optional<T> field) {
    if (field == null) {
      return Patch.absent();
    }
    return field.map(Patch::set).orElseGet(Patch::clear);
  }
}
