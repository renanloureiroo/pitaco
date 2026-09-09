package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyDisplayResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Coleta", description = "Superfície pública consumida pelo SDK")
public interface CollectDisplaySwagger {

  String PROBLEM_JSON = "application/problem+json";
  String KEY_HEADER = "X-Pitaco-Key";

  @Operation(
      summary = "Abre a exibição de exibição",
      description =
          """
          A abertura é ato explícito do SDK: pesquisa descartada por incompatibilidade de \
          renderização não vira exibição e não estraga a taxa de resposta.

          O identificador é gerado no dispositivo e é a chave de idempotência: a mesma abertura \
          de novo, com a mesma pesquisa e versão, devolve 200 com a exibição existente, sem criar \
          outra. O mesmo identificador para outra pesquisa ou versão é conflito.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = KEY_HEADER,
      required = true,
      description = "Chave da aplicação, exatamente como emitida",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Exibição aberta",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyDisplayResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da exibição aberta",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "200",
        description = "A mesma abertura de novo: a exibição existente, sem criar outra",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyDisplayResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Corpo malformado, identificador de exibição fora do formato UUID, ou respondente "
                + "sem nenhuma identificação (`request.invalid`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "`api_key.missing` ou `api_key.invalid`",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "Versão inexistente, em rascunho ou de outra aplicação — os três indistinguíveis "
                + "(`survey_version.not_found`)",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "Identificador já usado para outra pesquisa ou versão (`display.identifier_conflict`)",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "A aplicação da chave está inativa (`application.inactive`)",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyDisplayResponseDTO> open(
      OpenDisplayRequestDTO request, AuthenticatedApplication application);

  @Operation(
      summary = "Envia as respostas e o desfecho",
      description =
          """
          Um ato só, atômico: as respostas e o desfecho chegam juntos, o envio é validado inteiro \
          antes de qualquer gravação, e uma recusa deixa zero linhas.

          `COMPLETED` exige resposta para toda pergunta obrigatória; `DISMISSED` aceita o parcial \
          e preserva o que já veio. O envio continua sendo aceito se a pesquisa foi pausada, \
          encerrada ou republicada depois da abertura: a validação usa a versão exibida.

          A recusa por conteúdo devolve 422 `submission.rejected` com **todos** os problemas de \
          uma vez em `errors[]`, cada um com `questionKey` e um `code` entre \
          `answer.question_unknown`, `answer.question_duplicated`, `answer.required_missing`, \
          `answer.value_missing`, `answer.value_type_mismatch`, `answer.option_unknown`, \
          `answer.options_empty`, `answer.options_duplicated`, `answer.value_out_of_range` e \
          `answer.text_too_long`. Nunca há recusa genérica.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = KEY_HEADER,
      required = true,
      description = "Chave da aplicação, exatamente como emitida",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Gravado, ou reconhecido como reenvio idêntico"),
    @ApiResponse(
        responseCode = "400",
        description = "Corpo malformado ou desfecho desconhecido (`request.invalid`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "`api_key.missing` ou `api_key.invalid`",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Exibição inexistente ou de outra aplicação (`display.not_found`)",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "Exibição já fechada, e o envio traz desfecho diferente ou resposta nova "
                + "(`display.already_closed`)",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "`submission.rejected` com todos os problemas em `errors[]`, ou `application.inactive`",
        content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> submit(
      String displayId, SubmissionRequestDTO request, AuthenticatedApplication application);
}
