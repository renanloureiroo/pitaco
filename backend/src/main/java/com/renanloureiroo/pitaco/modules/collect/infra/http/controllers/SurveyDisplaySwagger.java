package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplaySummaryResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListDisplaysQueryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Exibições", description = "O que foi exibido a quem usa a aplicação, e como terminou")
public interface SurveyDisplaySwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Lista as exibições de uma pesquisa",
      description =
          "Paginado, da exibição mais recente para a mais antiga, com desempate determinístico. "
              + "Pesquisa que existe e nunca foi exibida devolve página vazia, não erro. "
              + "Número de versão que não existe na pesquisa também devolve página vazia: é "
              + "filtro sem resultado, não recurso inexistente. "
              + "Nenhuma exibição de outra aplicação aparece.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de exibições"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginação fora dos limites, número de versão malformado ou menor que 1, desfecho "
                + "desconhecido, instante malformado ou início do período posterior ao fim",
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
            "Pesquisa não encontrada. Mesma resposta para identificador malformado, pesquisa "
                + "inexistente e pesquisa de outra aplicação",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<DisplaySummaryResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @ParameterObject ListDisplaysQueryDTO query);
}
