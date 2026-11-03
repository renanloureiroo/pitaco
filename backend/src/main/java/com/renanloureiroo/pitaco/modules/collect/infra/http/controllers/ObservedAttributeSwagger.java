package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListObservedAttributesQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedAttributeResponseDTO;
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
    name = "Atributos observados",
    description =
        "Os atributos que o SDK de cada aplicação já enviou, para a autoria montar regras de "
            + "segmentação")
public interface ObservedAttributeSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Lista os atributos já observados numa aplicação",
      description =
          "Catálogo alimentado pela consulta de elegibilidade: cada nome aparece uma vez, com os "
              + "valores já vistos embutidos em ordem alfabética. Paginado por atributo, do visto "
              + "mais recentemente para o mais antigo. Catálogo, não perfil: nada liga um valor a "
              + "quem o enviou.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de atributos observados"),
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
  ResponseEntity<PageResponseDTO<ObservedAttributeResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @ParameterObject ListObservedAttributesQueryDTO query);
}
