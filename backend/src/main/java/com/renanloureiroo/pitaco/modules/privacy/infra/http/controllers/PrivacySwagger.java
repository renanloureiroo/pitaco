package com.renanloureiroo.pitaco.modules.privacy.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.DeletionAuditResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.EraseRespondentQueryDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.ListDeletionAuditsQueryDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RespondentErasureResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RetentionPreviewResponseDTO;
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
    name = "Privacidade",
    description = "Exclusão de respondente, registro das exclusões e prévia da retenção")
public interface PrivacySwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Exclui um respondente e tudo que ele respondeu nesta aplicação",
      description =
          "Irreversível. Apaga o respondente com as exibições, respostas, atributos e supressões "
              + "dele, e registra que houve exclusão, quando e quanto saiu — nunca quem. Os "
              + "resultados e o export param de contar essas respostas na hora. O agregado "
              + "congelado pela retenção é anônimo e não muda. Pedido repetido, ou para uma "
              + "identidade que nunca respondeu, devolve deleted=false e não cria registro.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Exclusão feita, ou nada a excluir"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Sem referência nem dispositivo, os dois ao mesmo tempo, ou identificação longa demais",
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
  ResponseEntity<RespondentErasureResponseDTO> erase(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @ParameterObject EraseRespondentQueryDTO query);

  @Operation(
      summary = "Lista as exclusões feitas numa aplicação",
      description =
          "Da mais recente para a mais antiga. Cada registro diz quando e quanto saiu, sem "
              + "nenhuma referência ao respondente excluído.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de registros de exclusão"),
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
  ResponseEntity<PageResponseDTO<DeletionAuditResponseDTO>> audits(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @ParameterObject ListDeletionAuditsQueryDTO query);

  @Operation(
      summary = "Prévia do próximo descarte pela política de retenção",
      description =
          "Quanto sai na próxima execução e até uma semana depois dela, e se algum descarte já "
              + "aconteceu. É o aviso que dá tempo de exportar antes do primeiro descarte. Sem "
              + "prazo configurado, configured=false e nada é previsto.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Prévia da retenção"),
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
  ResponseEntity<RetentionPreviewResponseDTO> retentionPreview(
      @Parameter(description = "Identificador da aplicação dona") String applicationId);
}
