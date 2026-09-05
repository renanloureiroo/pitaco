package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Aplicações", description = "Gerenciamento das aplicações que coletam pitacos")
public interface ApplicationControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Cria uma aplicação",
      description =
          "Registra uma aplicação ativa. O slug identifica a aplicação publicamente "
              + "e é derivado do nome quando não informado.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Aplicação criada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = CreateApplicationResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da aplicação criada",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome ou slug fora do formato aceito, ou prazo menor que um dia",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Já existe uma aplicação com o mesmo slug",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Prazo de retenção de texto livre maior que o prazo geral",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<CreateApplicationResponseDTO> create(CreateApplicationRequestDTO request);
}
