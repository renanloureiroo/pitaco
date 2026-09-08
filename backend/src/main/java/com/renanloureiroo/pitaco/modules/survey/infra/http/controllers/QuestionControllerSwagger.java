package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ReorderQuestionsRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.UpdateQuestionRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Perguntas", description = "O conteúdo do rascunho: enunciado, tipo, ordem e opções")
public interface QuestionControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";
  String FROZEN =
      "Conteúdo publicado está congelado (survey.content_frozen) — não há rascunho aberto para "
          + "receber a escrita";

  @Operation(
      summary = "Acrescenta uma pergunta ao rascunho",
      description =
          "A pergunta entra na última posição, com uma chave estável nova. Pergunta de escolha "
              + "sem nenhuma opção é aceita: a pendência vira impedimento de publicação.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Pergunta acrescentada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = QuestionResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da pergunta criada",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Corpo inválido, opção em tipo que não aceita opção, opções repetidas ou faixa "
                + "numérica incoerente",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = FROZEN,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<QuestionResponseDTO> add(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      AddQuestionRequestDTO request);

  @Operation(
      summary = "Reescreve uma pergunta",
      description = "A chave estável e a posição permanecem: é a mesma pergunta, com outro texto.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Pergunta reescrita",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = QuestionResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Corpo inválido ou incoerência entre tipo, opções e faixa",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa ou pergunta não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = FROZEN,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<QuestionResponseDTO> update(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @Parameter(description = "Identificador da pergunta") String questionId,
      UpdateQuestionRequestDTO request);

  @Operation(
      summary = "Remove uma pergunta do rascunho",
      description = "As posições das demais são recompactadas, sem deixar buraco.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Pergunta removida"),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa ou pergunta não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = FROZEN,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> remove(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @Parameter(description = "Identificador da pergunta") String questionId);

  @Operation(
      summary = "Redefine a ordem das perguntas",
      description =
          "O corpo traz a permutação exata dos identificadores existentes. A integridade é "
              + "verificada no resultado final, não a cada passo.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Perguntas na nova ordem",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array =
                    @ArraySchema(schema = @Schema(implementation = QuestionResponseDTO.class)))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Corpo inválido, ou lista que não é permutação exata das perguntas existentes "
                + "(question.order_invalid)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = FROZEN,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<List<QuestionResponseDTO>> reorder(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      ReorderQuestionsRequestDTO request);
}
