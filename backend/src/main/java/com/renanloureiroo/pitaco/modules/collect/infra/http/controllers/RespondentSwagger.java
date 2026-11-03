package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListDisplaysQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListRespondentsQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDisplayResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Respondentes", description = "Quem a aplicação já viu, e o que já lhe foi exibido")
public interface RespondentSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Lista os respondentes de uma aplicação",
      description =
          "Paginado, do último contato mais recente para o mais antigo. Aplicação sem nenhum "
              + "respondente devolve página vazia, não erro. Nenhum respondente de outra "
              + "aplicação aparece.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de respondentes"),
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
  ResponseEntity<PageResponseDTO<RespondentResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @ParameterObject ListRespondentsQueryDTO query);

  @Operation(
      summary = "Lista as exibições de um respondente",
      description =
          "A mesma listagem de exibições pelo eixo do respondente, cada linha apontando a "
              + "pesquisa que a originou. O filtro por versão não se aplica aqui, porque as "
              + "exibições podem ser de pesquisas diferentes.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de exibições do respondente"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginação fora dos limites, desfecho desconhecido, instante malformado ou início "
                + "do período posterior ao fim",
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
        description =
            "Respondente não encontrado. Mesma resposta para identificador malformado, "
                + "respondente inexistente e respondente de outra aplicação",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<RespondentDisplayResponseDTO>> listDisplays(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador do respondente") String respondentId,
      @ParameterObject ListDisplaysQueryDTO query);
}
