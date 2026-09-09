package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationSummaryResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationRequestDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ListApplicationsQueryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
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
                schema = @Schema(implementation = String.class))),
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

  @Operation(
      summary = "Lista as aplicações cadastradas",
      description =
          "Devolve, paginadas, as aplicações da mais recente para a mais antiga — ativas e "
              + "inativas, salvo filtro em contrário. A linha traz o essencial para identificar "
              + "a aplicação; os prazos de política ficam na consulta individual.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "Página de aplicações. Lista vazia quando nada atende ao filtro, ou quando a página "
                + "pedida está além do fim do conjunto"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Parâmetro de paginação fora dos limites (página negativa, tamanho fora de 1..100) "
                + "ou estado diferente de active/inactive",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "Requisição apresentou o header de chave de aplicação: a chave do SDK não vale no "
                + "painel",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<ApplicationSummaryResponseDTO>> list(
      @ParameterObject ListApplicationsQueryDTO query);

  @Operation(
      summary = "Consulta uma aplicação",
      description =
          "Devolve a aplicação inteira, com os prazos de política e o instante da última "
              + "alteração. Prazo não configurado vem ausente do corpo, nunca zero. Encontra "
              + "também aplicação inativa. Identificador inexistente e identificador malformado "
              + "respondem igual, para não entregar um oráculo de formato a quem sonda a API.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Aplicação encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApplicationResponseDTO.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "Requisição apresentou o header de chave de aplicação: a chave do SDK não vale no "
                + "painel",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "Aplicação não encontrada, ou identificador em formato inválido — indistinguíveis "
                + "por decisão de projeto",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<ApplicationResponseDTO> get(
      @Parameter(description = "Identificador da aplicação, devolvido na criação")
          String applicationId);
}
