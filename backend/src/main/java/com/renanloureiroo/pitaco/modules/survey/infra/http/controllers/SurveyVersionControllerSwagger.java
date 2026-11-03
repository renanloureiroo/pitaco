package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ListSurveyVersionsQueryDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.VersionComparabilityResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Versões", description = "O que foi congelado, e o que pode ser comparado com o quê")
public interface SurveyVersionControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";
  String NOT_FOUND = "Pesquisa não encontrada";

  @Operation(
      summary = "Lista as versões publicadas",
      description = "Cada uma com número, instante de publicação, classificação e resumo.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de versões, da mais recente para a mais antiga"),
    @ApiResponse(
        responseCode = "400",
        description = "Parâmetro de paginação fora dos limites",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<SurveyVersionResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @ParameterObject ListSurveyVersionsQueryDTO query);

  @Operation(
      summary = "Abre um rascunho de nova versão",
      description =
          "Cópia integral do conteúdo da versão publicada — perguntas com suas chaves estáveis, "
              + "disparo e regras. No máximo um rascunho de versão por pesquisa.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Rascunho de versão aberto",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyVersionResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI do rascunho de versão",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Já existe um rascunho de versão aberto (survey_version.draft_already_open)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "Pesquisa nunca publicada (survey.not_published) ou encerrada "
                + "(survey.transition_not_allowed)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyVersionResponseDTO> open(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "Descarta o rascunho de versão",
      description = "A pesquisa volta a ter apenas a versão publicada, intacta.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Rascunho de versão descartado"),
    @ApiResponse(
        responseCode = "404",
        description =
            "Pesquisa não encontrada, ou sem rascunho de versão aberto "
                + "(survey_version.not_found)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> discardDraft(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "Consulta o conteúdo de uma versão",
      description =
          "Perguntas com enunciado, tipo, ordem, obrigatoriedade, opções e chave estável, mais o "
              + "disparo e as regras congelados nela.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Versão encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyVersionDetailResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "Pesquisa não encontrada (survey.not_found) ou versão inexistente "
                + "(survey_version.not_found)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyVersionDetailResponseDTO> get(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @Parameter(description = "Número da versão, a partir de 1") int number);

  @Operation(
      summary = "Quais versões são comparáveis entre si",
      description =
          "Versões separadas apenas por mudanças cosméticas formam um grupo; uma mudança "
              + "semântica abre um grupo novo. A comparabilidade é transitiva dentro do grupo.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Grupos de versões comparáveis",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = VersionComparabilityResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<VersionComparabilityResponseDTO> comparability(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);
}
