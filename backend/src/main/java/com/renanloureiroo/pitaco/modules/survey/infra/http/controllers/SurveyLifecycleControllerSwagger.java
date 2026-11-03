package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyStateTransitionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Ciclo de vida", description = "Parar, retomar e encerrar o que está no ar")
public interface SurveyLifecycleControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";
  String NOT_FOUND = "Pesquisa não encontrada";

  @Operation(
      summary = "Pausa uma pesquisa ativa",
      description = "Imediato e reversível. A transição fica registrada com instante e motivo.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Pesquisa pausada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "Pesquisa ainda em rascunho (survey.not_published), já pausada ou encerrada "
                + "(survey.transition_not_allowed)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyResponseDTO> pause(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "Retoma uma pesquisa pausada",
      description = "A janela decide se ela volta agendada ou ativa.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Pesquisa retomada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "Pesquisa ainda em rascunho (survey.not_published), já no ar ou encerrada "
                + "(survey.transition_not_allowed)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyResponseDTO> resume(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(summary = "Encerra a pesquisa", description = "Encerramento é definitivo.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Pesquisa encerrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "Pesquisa ainda em rascunho (survey.not_published) ou já encerrada "
                + "(survey.transition_not_allowed)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyResponseDTO> end(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "Histórico de transições de estado",
      description =
          "As transições comandadas somadas às derivadas da janela já passada, ordenadas por "
              + "instante. As de janela não são gravadas: seu instante é o limite da janela, não "
              + "o da leitura.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Histórico",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array =
                    @ArraySchema(
                        schema =
                            @Schema(implementation = SurveyStateTransitionResponseDTO.class)))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<List<SurveyStateTransitionResponseDTO>> transitions(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);
}
