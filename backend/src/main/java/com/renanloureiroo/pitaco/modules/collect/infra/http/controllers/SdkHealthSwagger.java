package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ListSdkErrorsQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkErrorReportResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkVersionsResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyHealthQueryDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyHealthResponseDTO;
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
    name = "Saúde e compatibilidade",
    description =
        "O que acontece do outro lado: versões do SDK em uso, supressões por incompatibilidade "
            + "e falhas do próprio SDK")
public interface SdkHealthSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Distribuição das versões do SDK na aplicação",
      description =
          """
          Cada versão que já consultou a elegibilidade, com o acumulado desde a primeira vez \
          vista e a proporção do tráfego recente (14 dias por padrão). Versão que não aparece \
          há mais que o limite configurado vem marcada como `stale`, para que se possa parar de \
          sustentá-la com base em dado e não em palpite.

          As contagens são aproximadas: o servidor acumula em memória e grava em lote, e o que \
          não foi gravado se perde num reinício.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Versões da aplicação"),
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
  ResponseEntity<SdkVersionsResponseDTO> versions(
      @Parameter(description = "Identificador da aplicação") String applicationId);

  @Operation(
      summary = "Lista os relatórios de falha do SDK",
      description =
          "Do recebido mais recentemente para o mais antigo. Relatórios com mais de 90 dias são "
              + "expurgados.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de relatórios"),
    @ApiResponse(
        responseCode = "400",
        description = "Filtro ou paginação fora dos limites",
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
  ResponseEntity<PageResponseDTO<SdkErrorReportResponseDTO>> errors(
      @Parameter(description = "Identificador da aplicação") String applicationId,
      @ParameterObject ListSdkErrorsQueryDTO query);

  @Operation(
      summary = "Saúde de uma pesquisa",
      description =
          """
          Supressões por incompatibilidade no período, com motivo e versão do SDK, contra as \
          exibições do mesmo período. `relevant` diz quando vale avisar: proporção de 5% ou \
          mais e ao menos 5 supressões, por padrão.

          `eventLastSeenAt` ausente, com `eventName` presente, significa que o evento do \
          disparo nunca chegou desta aplicação — um motivo de zero respostas distinto da \
          supressão, e que pede correção no disparo, não na pesquisa.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Saúde da pesquisa no período"),
    @ApiResponse(
        responseCode = "400",
        description = "Período invertido ou instante malformado",
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
        description = "Pesquisa não encontrada nesta aplicação",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyHealthResponseDTO> surveyHealth(
      @Parameter(description = "Identificador da aplicação") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @ParameterObject SurveyHealthQueryDTO query);
}
