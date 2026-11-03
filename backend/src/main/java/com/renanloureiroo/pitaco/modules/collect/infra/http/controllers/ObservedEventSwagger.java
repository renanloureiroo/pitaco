package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListObservedEventsQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedEventResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(
    name = "Eventos observados",
    description = "Os eventos que o SDK de cada aplicação já consultou, para a autoria escolher")
public interface ObservedEventSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Lista os eventos já observados numa aplicação",
      description =
          "Catálogo alimentado pela consulta de elegibilidade: cada nome de evento aparece uma "
              + "vez, com a primeira e a última ocorrência. Paginado, do visto mais "
              + "recentemente para o mais antigo. Aplicação que nunca recebeu consulta devolve "
              + "página vazia, não erro.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de eventos observados"),
    @ApiResponse(
        responseCode = "400",
        description = "Parâmetro de paginação fora dos limites",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Chave de aplicação apresentada na superfície administrativa",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Aplicação não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<ObservedEventResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @ParameterObject ListObservedEventsQueryDTO query);
}
