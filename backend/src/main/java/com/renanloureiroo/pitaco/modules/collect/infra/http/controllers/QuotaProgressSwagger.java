package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.QuotaProgressResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Cota de respostas", description = "O progresso de cada pesquisa rumo à sua cota")
public interface QuotaProgressSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Progresso da pesquisa rumo à cota",
      description =
          "Concluídas em todas as versões, ao lado da cota configurada. Sem cota, a cota vem "
              + "ausente e a contagem continua valendo. Atingida a cota, a pesquisa é encerrada "
              + "pela própria conclusão que a atingiu, com motivo quota_reached no histórico.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Progresso da cota",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = QuotaProgressResponseDTO.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Chave de aplicação apresentada na superfície administrativa",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada nesta aplicação",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<QuotaProgressResponseDTO> get(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);
}
