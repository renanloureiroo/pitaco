package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplayDetailResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Exibições", description = "O que foi exibido a quem usa a aplicação, e como terminou")
public interface DisplaySwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Consulta uma exibição com suas respostas",
      description =
          "Devolve o desfecho, o instantâneo de atributos, a versão do SDK, o respondente e as "
              + "respostas na ordem das perguntas da versão exibida. Exibição ainda aberta "
              + "devolve nenhuma resposta e nenhum fechamento, não erro. Texto livre cujo prazo "
              + "de retenção da aplicação já venceu volta com situação EXPIRED e sem o texto.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Exibição encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DisplayDetailResponseDTO.class))),
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
            "Exibição não encontrada. Mesma resposta para identificador malformado, exibição "
                + "inexistente e exibição de outra aplicação",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<DisplayDetailResponseDTO> get(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da exibição") String displayId);
}
