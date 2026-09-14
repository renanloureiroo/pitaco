package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsReceiptDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Coleta", description = "Superfície pública consumida pelo SDK")
public interface CollectInteractionSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Envia um lote de eventos de interação de uma exibição",
      description =
          """
          Os eventos do catálogo do Pitaco (componente `InteractionEvent`, tipos em \
          `InteractionEventType`), emitidos pelo core do SDK como consequência das transições da \
          pesquisa. Envie depois de a abertura da exibição ter recebido 201 ou 200.

          O par (`displayId`, `seq`) é a chave de idempotência: reenviar o mesmo lote não grava \
          nada de novo, e o primeiro evento de cada `seq` vence. Responde 202 sempre que o corpo \
          é legível, inclusive quando descarta: exibição inexistente, de outra aplicação ou de \
          aplicação inativa descarta o lote inteiro sem revelar qual dos três é o caso.

          Evento a evento, nunca o lote: tipo fora do catálogo, `catalogVersion` mais nova com \
          tipo que o servidor não conhece, envelope incompleto e pergunta fora da versão exibida \
          são descartados e contados. Campo extra e campo do `data` fora da forma do catálogo \
          são ignorados; em evento de texto só `length` sobrevive. Há teto de eventos por \
          exibição e janela de aceitação contada da abertura, ambos configuráveis.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Pitaco-Key",
      required = true,
      description = "Chave da aplicação, exatamente como emitida",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Recebido; o corpo conta o que foi gravado, repetido e descartado",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = InteractionEventsReceiptDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Corpo malformado, lote vazio ou com mais de 100 eventos, ou campo do envelope com "
                + "tipo JSON errado (`request.invalid`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description =
            "Chave ausente (`api_key.missing`), desconhecida ou revogada (`api_key.invalid`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Limite de requisições da chave ou da origem (`rate_limit.exceeded`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<InteractionEventsReceiptDTO> record(
      @Parameter(description = "Identificador da exibição, o UUID gerado no dispositivo")
          String displayId,
      InteractionEventsRequestDTO request,
      AuthenticatedApplication application);
}
