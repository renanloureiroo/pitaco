package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.core.usecase.Patch;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.UpdateSurveyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import tools.jackson.databind.annotation.JsonDeserialize;

// Mesmo molde do PATCH de aplicação: ausente chega como referência nula e não mexe; na cota,
// null chega Optional vazio e remove. Os demais não são removíveis e recusam null.
@JsonDeserialize(using = UpdateSurveyRequestDeserializer.class)
@Schema(
    description =
        "Campos a alterar numa pesquisa. Campo omitido não muda; cota enviada como null é "
            + "removida. Nenhum deles abre versão: nome e exposição são da pesquisa")
public record UpdateSurveyRequestDTO(
    @Schema(
            description = "Nome pelo qual a pesquisa passa a ser reconhecida",
            example = "NPS pós-entrega",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<
                @NotBlank(message = "Nome é obrigatório")
                @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
                String>
            name,
    @Schema(
            description =
                "Prioridade no desempate entre pesquisas que disputam o mesmo evento; maior vence",
            example = "10",
            minimum = "-100",
            maximum = "100",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<
                @Min(value = -100, message = "A prioridade deve estar entre -100 e 100")
                @Max(value = 100, message = "A prioridade deve estar entre -100 e 100")
                Integer>
            priority,
    @Schema(
            description = "Respostas concluídas que encerram a pesquisa sozinha. null remove",
            example = "100",
            minimum = "1",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<@Positive(message = "A cota de respostas deve ser de ao menos uma") Integer>
            responseQuota,
    @Schema(
            description = "Se a pesquisa ignora o intervalo de descanso da aplicação",
            example = "false",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<Boolean> ignoresQuietPeriod,
    @Schema(
            description =
                "Se o aviso para não escrever dado pessoal aparece junto dos campos de texto livre",
            example = "true",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<Boolean> freeTextNoticeEnabled,
    @Schema(
            description = "Texto próprio do aviso. null volta ao texto padrão",
            example = "Não escreva telefone nem e-mail aqui.",
            maxLength = 200,
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Optional<
                // @Pattern e não @NotBlank: null aqui é "volta ao padrão", e @NotBlank o recusaria.
                @Pattern(regexp = "(?s).*\\S.*", message = "O texto do aviso não pode ser vazio")
                @Size(max = 200, message = "O texto do aviso não pode passar de 200 caracteres")
                String>
            freeTextNoticeText) {

  public UpdateSurveyRequestDTO(
      Optional<String> name,
      Optional<Integer> priority,
      Optional<Integer> responseQuota,
      Optional<Boolean> ignoresQuietPeriod) {
    this(name, priority, responseQuota, ignoresQuietPeriod, null, null);
  }

  public UpdateSurveyUseCase.Input toInput(String applicationId, String surveyId) {
    return new UpdateSurveyUseCase.Input(
        applicationId,
        surveyId,
        orEmpty(name),
        orEmpty(priority),
        patchOf(responseQuota),
        orEmpty(ignoresQuietPeriod),
        orEmpty(freeTextNoticeEnabled),
        patchOf(freeTextNoticeText));
  }

  private static <T> Optional<T> orEmpty(Optional<T> field) {
    return field == null ? Optional.empty() : field;
  }

  private static <T> Patch<T> patchOf(Optional<T> field) {
    if (field == null) {
      return Patch.absent();
    }
    return field.map(Patch::set).orElseGet(Patch::clear);
  }
}
