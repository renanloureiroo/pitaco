package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkErrorReportRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SuppressionRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Coleta", description = "Superfície pública consumida pelo SDK")
public interface CollectHealthSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Sinaliza uma pesquisa elegível que o SDK não soube renderizar",
      description =
          """
          Chamada quando o SDK recebeu uma pesquisa e, depois de descartar o que não sabe \
          desenhar, não sobrou pergunta nenhuma. Nada foi exibido e nenhuma exibição deve ser \
          aberta: supressão não conta na taxa de resposta.

          Responde 202 sem corpo tanto quando grava quanto quando descarta: pesquisa de outra \
          aplicação, versão que não é publicada, aplicação inativa ou a mesma supressão do \
          mesmo respondente na mesma versão nas últimas 24 horas. O SDK não precisa distinguir \
          os casos, e a resposta não revela a existência de dado de outra aplicação.

          A identificação do respondente não é gravada e não cria respondente: serve só para não \
          contar duas vezes a mesma supressão.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Pitaco-Key",
      required = true,
      description = "Chave da aplicação, exatamente como emitida",
      schema = @Schema(type = "string", example = "pit_a1b2c3d4_..."))
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Recebida; gravada ou descartada"),
    @ApiResponse(
        responseCode = "400",
        description = "Corpo malformado ou constraint violada",
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
  ResponseEntity<Void> suppress(
      SuppressionRequestDTO request,
      @Parameter(
              in = ParameterIn.HEADER,
              name = SdkVersionHeader.NAME,
              required = false,
              description = "Versão semver do SDK que suprimiu; inválida é ignorada",
              schema = @Schema(type = "string", example = "1.4.2", maxLength = 40))
          String sdkVersion,
      AuthenticatedApplication application);

  @Operation(
      summary = "Reporta uma falha interna do SDK",
      description =
          """
          Canal próprio do Pitaco para falhas do SDK, separado da ferramenta de erro do app \
          hospedeiro e desligável pelo integrador. O relatório não pode carregar dado do \
          usuário nem conteúdo de resposta: o servidor sanitiza o contexto e mascara e-mail e \
          número longo na mensagem, como rede de segurança e não como licença.

          Tem limite de requisições próprio por chave, mais baixo e separado do da consulta de \
          elegibilidade: uma tempestade de erros não consome a cota das consultas. Responde 202 \
          sem corpo; relatório de aplicação inativa é descartado com a mesma resposta.

          O SDK deve desistir em silêncio quando este envio falhar, sem gerar novo relatório.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Pitaco-Key",
      required = true,
      description = "Chave da aplicação, exatamente como emitida",
      schema = @Schema(type = "string", example = "pit_a1b2c3d4_..."))
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Recebido; gravado ou descartado"),
    @ApiResponse(
        responseCode = "400",
        description = "Corpo malformado ou constraint violada",
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
        description = "Limite de relatórios da chave ou da origem (`rate_limit.exceeded`)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> report(
      SdkErrorReportRequestDTO request,
      @Parameter(
              in = ParameterIn.HEADER,
              name = SdkVersionHeader.NAME,
              required = false,
              description = "Versão semver do SDK que falhou; inválida é ignorada",
              schema = @Schema(type = "string", example = "1.4.2", maxLength = 40))
          String sdkVersion,
      AuthenticatedApplication application);
}
