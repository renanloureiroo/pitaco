package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApiKeyResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyRequestDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ListApiKeysQueryDTO;
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

@Tag(name = "Chaves de API", description = "Credenciais que identificam uma aplicação no Pitaco")
public interface ApiKeyControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Lista as chaves de API de uma aplicação",
      description =
          "Devolve, paginadas, as chaves da aplicação informada — válidas e revogadas, salvo "
              + "filtro em contrário. Nenhum segredo aparece aqui: o texto claro existe apenas "
              + "na resposta da emissão. Funciona também para aplicação inativa: inatividade "
              + "impede emitir, não impede enxergar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "Página de chaves. Lista vazia quando a aplicação existe e nenhuma chave atende ao "
                + "filtro, ou quando a página pedida está além do fim do conjunto"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Parâmetro de paginação fora dos limites (página negativa, tamanho fora de 1..100) "
                + "ou estado diferente de active/revoked",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "Aplicação não encontrada, ou identificador de aplicação em formato inválido — "
                + "indistinguíveis por decisão de projeto",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<ApiKeyResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona da chave") String applicationId,
      @ParameterObject ListApiKeysQueryDTO query);

  @Operation(
      summary = "Consulta uma chave de API",
      description =
          "Devolve os dados de uma única chave, no contexto da aplicação que a emitiu. Encontra "
              + "também chaves revogadas, apresentadas com o instante da revogação. Nenhum "
              + "segredo é devolvido.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Chave encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiKeyResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "Aplicação não encontrada (application.not_found), ou chave não encontrada, "
                + "pertencente a outra aplicação, ou com identificador em formato inválido "
                + "(api_key.not_found) — os três últimos indistinguíveis por decisão de projeto",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<ApiKeyResponseDTO> get(
      @Parameter(description = "Identificador da aplicação dona da chave") String applicationId,
      @Parameter(description = "Identificador da chave, devolvido na emissão") String apiKeyId);

  @Operation(
      summary = "Emite uma chave de API",
      description =
          "Emite uma chave para a aplicação informada. O segredo em texto claro é devolvido "
              + "uma única vez, nesta resposta, e não é recuperável por nenhuma outra operação.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Chave emitida",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = IssueApiKeyResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da chave criada",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Rótulo ausente, em branco ou acima de 80 caracteres",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Aplicação não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Aplicação inativa",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<IssueApiKeyResponseDTO> issue(
      @Parameter(description = "Identificador da aplicação dona da chave") String applicationId,
      IssueApiKeyRequestDTO request);

  @Operation(
      summary = "Exclui uma chave de API",
      description =
          "Revoga a chave: ela deixa de ser válida imediatamente e de forma irreversível. "
              + "O registro permanece armazenado — rótulo, prefixo público, instante de criação "
              + "e instante da revogação —, sem nenhum vestígio do segredo.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Chave revogada"),
    @ApiResponse(
        responseCode = "404",
        description =
            "Chave não encontrada, pertencente a outra aplicação, ou identificador em formato "
                + "inválido — indistinguíveis por decisão de projeto",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Chave já revogada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> revoke(
      @Parameter(description = "Identificador da aplicação dona da chave") String applicationId,
      @Parameter(description = "Identificador da chave, devolvido na emissão") String apiKeyId);
}
