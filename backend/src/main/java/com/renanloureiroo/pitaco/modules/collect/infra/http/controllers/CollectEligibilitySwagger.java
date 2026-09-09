package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;

@Tag(name = "Coleta", description = "Superfície pública consumida pelo SDK")
public interface CollectEligibilitySwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Há pesquisa para este respondente agora?",
      description =
          """
          Aplica as camadas de elegibilidade na ordem: aplicação ativa, pesquisa no ar, janela \
          aberta, evento exato, histórico do respondente, segmentação e sorteio. Devolve no \
          máximo uma pesquisa, com a versão publicada inteira.

          Quando mais de uma sobrevive, vence a de publicação mais antiga; empate técnico é \
          desfeito pelo identificador da pesquisa em ordem lexicográfica, de modo que a mesma \
          consulta repetida devolve sempre a mesma pesquisa.

          Aplicação inativa, pesquisa pausada, fora da janela, evento sem pesquisa, regra não \
          satisfeita, não sorteado e já resolvida não são erro: todos devolvem `survey` nulo, e \
          nada é gravado.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Pitaco-Key",
      required = true,
      description = "Chave da aplicação, exatamente como emitida",
      schema = @Schema(type = "string", example = "pit_a1b2c3d4_..."))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "A pesquisa a exibir, ou `survey` nulo quando não há nenhuma",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EligibilityResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Corpo malformado ou constraint violada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Chave ausente (`api_key.missing`), desconhecida ou revogada "
            + "(`api_key.invalid`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  EligibilityResponseDTO check(
      EligibilityRequestDTO request, AuthenticatedApplication application);
}
