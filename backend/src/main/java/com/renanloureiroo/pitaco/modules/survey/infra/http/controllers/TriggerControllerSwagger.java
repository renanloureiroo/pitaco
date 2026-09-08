package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddSegmentationRuleRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DefineTriggerRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SegmentationRuleResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.TriggerResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Disparo", description = "Quando a pesquisa aparece, e para quem")
public interface TriggerControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";
  String FROZEN =
      "Conteúdo publicado está congelado (survey.content_frozen) — não há rascunho aberto para "
          + "receber a escrita";

  @Operation(
      summary = "Define ou substitui o disparo",
      description =
          "Existe sempre um único disparo por versão: definir de novo substitui o anterior, "
              + "preservando as regras já penduradas nele. Fim de janela ausente significa tempo "
              + "indeterminado.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Disparo definido",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TriggerResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Evento fora do formato, janela com fim não posterior ao início, ou proporção fora "
                + "de 0 a 1",
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
  ResponseEntity<TriggerResponseDTO> define(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      DefineTriggerRequestDTO request);

  @Operation(
      summary = "Acrescenta uma regra de segmentação",
      description = "As regras se penduram no disparo, então ele precisa existir antes.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Regra acrescentada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SegmentationRuleResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da regra criada",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Atributo vazio, operação desconhecida, valor ausente em equals/not_equals ou valor "
                + "presente em present/absent",
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
        description = "Disparo ainda não definido (trigger.not_defined), ou " + FROZEN,
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SegmentationRuleResponseDTO> addRule(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      AddSegmentationRuleRequestDTO request);

  @Operation(
      summary = "Remove uma regra de segmentação",
      description = "As demais regras continuam intactas.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Regra removida"),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa ou regra não encontrada",
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
  ResponseEntity<Void> removeRule(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @Parameter(description = "Identificador da regra") String ruleId);
}
