package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.OpenAnswerResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.OpenAnswersQueryDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.ResultsQueryDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Resultados", description = "O que foi respondido, quem viu sem responder, e o export")
public interface SurveyResultsSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Agregados por pergunta e taxa de resposta de uma pesquisa",
      description =
          "Consulta direta ao banco sob o recorte informado: período de abertura, atributo do "
              + "respondente e versão. `attribute` sem `attributeValue` seleciona quem não "
              + "enviou o atributo. Pergunta sem nenhuma resposta vem com `aggregate` ausente, "
              + "distinto de zero. Pesquisa que nunca publicou responde 200 vazio com "
              + "`everPublished: false`.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resultado sob o recorte"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Instante malformado, período invertido, versão menor que 1, atributo vazio ou "
                + "valor de atributo sem nome",
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
  ResponseEntity<SurveyResultsResponseDTO> results(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @ParameterObject ResultsQueryDTO query);

  @Operation(
      summary = "Respostas de texto livre, paginadas e com busca",
      description =
          "Da mais recente para a mais antiga, sob o mesmo recorte dos agregados. Texto vazio "
              + "ou só com espaço não aparece, nem texto vencido pela retenção da aplicação. "
              + "Cada item traz as demais respostas da mesma exibição.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de respostas abertas"),
    @ApiResponse(
        responseCode = "400",
        description = "Paginação fora dos limites, recorte inválido ou termo longo demais",
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
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<OpenAnswerResponseDTO>> openAnswers(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @ParameterObject OpenAnswersQueryDTO query);

  @Operation(
      summary = "Exporta as exibições em CSV",
      description =
          "Uma linha por exibição, dispensadas e abandonadas inclusive, sob o mesmo recorte. "
              + "Colunas fixas, uma por atributo visto no recorte e uma por pergunta; múltipla "
              + "escolha separa opções por ponto e vírgula. UTF-8 com BOM, escapado conforme a "
              + "RFC 4180, transmitido em fluxo. Só a referência opaca do respondente viaja. "
              + "Resposta descartada pela retenção deixa a célula vazia: a exibição continua, a "
              + "resposta não. Quando a pesquisa tem texto livre, a resposta traz o cabeçalho "
              + "X-Pitaco-Content-Warning: may-contain-personal-data, porque texto livre é por "
              + "onde dado pessoal entra sem ninguém pedir.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Arquivo CSV",
        headers =
            @Header(
                name = "X-Pitaco-Content-Warning",
                description =
                    "may-contain-personal-data quando alguma versão publicada tem texto livre",
                schema = @Schema(type = "string")),
        content = @Content(mediaType = "text/csv")),
    @ApiResponse(
        responseCode = "400",
        description = "Recorte inválido",
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
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<StreamingResponseBody> export(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      @ParameterObject ResultsQueryDTO query);
}
