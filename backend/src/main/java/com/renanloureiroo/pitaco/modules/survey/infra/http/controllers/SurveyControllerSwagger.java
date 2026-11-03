package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.infra.http.error.ApiErrorResponse;
import com.renanloureiroo.pitaco.infra.http.error.ApiValidationErrorResponse;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DuplicateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ListSurveysQueryDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationImpedimentsResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationWarningsResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublishSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.UpdateSurveyRequestDTO;
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

@Tag(name = "Pesquisas", description = "O que se pergunta a quem usa a aplicação, e quando")
public interface SurveyControllerSwagger {

  String PROBLEM_JSON = "application/problem+json";

  @Operation(
      summary = "Cria uma pesquisa em rascunho",
      description =
          "A pesquisa nasce em rascunho, sem disparo e sem versão publicada, junto de sua versão"
              + " 1 em rascunho. Sem modelo, nasce sem perguntas; com nps, csat ou ces, nasce com"
              + " a pergunta do formato — enunciado, escala e rótulos —, editável como qualquer"
              + " outra. Exige aplicação existente e ativa.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Pesquisa criada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da pesquisa criada",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome ausente, em branco ou acima de 120 caracteres, ou modelo desconhecido",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "Aplicação não encontrada, ou identificador em formato inválido — indistinguíveis "
                + "por decisão de projeto",
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
  ResponseEntity<SurveyResponseDTO> create(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      CreateSurveyRequestDTO request);

  @Operation(
      summary = "Duplica uma pesquisa, na mesma aplicação ou em outra",
      description =
          "Copia as perguntas — com chaves novas e condições reapontadas —, o disparo, as "
              + "regras de segmentação, a exposição e o modelo de origem, a partir da versão "
              + "publicada corrente, ou do rascunho de quem nunca publicou. A cópia nasce em "
              + "rascunho, versão 1, sem respostas e sem histórico. Janela de disparo já "
              + "encerrada vira janela aberta a partir da duplicação e sem fim.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Cópia criada em rascunho",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da cópia, sob a aplicação de destino",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome em branco ou acima de 120 caracteres, ou corpo malformado",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa de origem ou aplicação de destino não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Aplicação de destino inativa",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyResponseDTO> duplicate(
      @Parameter(description = "Identificador da aplicação dona da original") String applicationId,
      @Parameter(description = "Identificador da pesquisa original") String surveyId,
      DuplicateSurveyRequestDTO request);

  @Operation(
      summary = "Lista as pesquisas de uma aplicação",
      description =
          "Paginado, da mais recente para a mais antiga. Aplicação sem pesquisa devolve página "
              + "vazia, não erro. Nenhuma pesquisa de outra aplicação aparece.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de pesquisas"),
    @ApiResponse(
        responseCode = "400",
        description = "Parâmetro de paginação fora dos limites",
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
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PageResponseDTO<SurveyResponseDTO>> list(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @ParameterObject ListSurveysQueryDTO query);

  @Operation(
      summary = "Consulta uma pesquisa",
      description =
          "Devolve nome, estado derivado, aplicação dona, instante de criação e o conteúdo "
              + "montado — o do rascunho quando existe, o da versão publicada caso contrário, "
              + "indicado por content.source.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Pesquisa encontrada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyDetailResponseDTO.class))),
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
  ResponseEntity<SurveyDetailResponseDTO> get(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "Edita nome e exposição da pesquisa",
      description =
          "Nome, prioridade no desempate, cota de respostas e isenção do intervalo de descanso. "
              + "Campo omitido não muda; cota null é removida. Nenhum deles abre versão nem "
              + "muda o estado, e todos valem também com a pesquisa no ar. A cota é conferida "
              + "a cada resposta concluída, e também aqui: reduzi-la até o total já concluído "
              + "encerra a pesquisa na hora, com o motivo quota_reached no histórico.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Pesquisa alterada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyResponseDTO.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Nome em branco ou acima de 120 caracteres, prioridade fora de -100 a 100, cota "
                + "abaixo de 1, null em campo não removível, ou corpo malformado",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyResponseDTO> update(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      UpdateSurveyRequestDTO request);

  @Operation(
      summary = "Descarta uma pesquisa nunca publicada",
      description = "Publicada não se apaga, se encerra.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Descartada, junto do conteúdo montado nela"),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Pesquisa já publicada (survey.published_cannot_be_discarded)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<Void> discard(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "O que ainda falta para publicar",
      description =
          "Devolve a lista completa de pendências sem publicar nada. É exatamente a mesma lista "
              + "que a publicação usaria para recusar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lista de impedimentos; vazia quando o rascunho está publicável",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PublicationImpedimentsResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PublicationImpedimentsResponseDTO> checkPublication(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "O que convém saber antes de publicar",
      description =
          "Avisos que não impedem a publicação: regra de segmentação que exige atributo ou valor "
              + "que o app nunca enviou (segmentation.no_known_match), regras que se contradizem "
              + "(segmentation.contradictory) e outras pesquisas no ar ou agendadas escutando o "
              + "mesmo evento (trigger.competing_surveys). Lê o rascunho quando existe e a "
              + "versão publicada caso contrário.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lista de avisos; vazia quando não há nada a observar",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PublicationWarningsResponseDTO.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<PublicationWarningsResponseDTO> checkPublicationWarnings(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId);

  @Operation(
      summary = "Publica o rascunho, congelando o conteúdo em uma versão",
      description =
          "Valida antes de congelar e, recusando, relata todos os impedimentos de uma vez. A "
              + "partir da versão 2, changeKind e changeSummary são obrigatórios. Publicada, a "
              + "pesquisa fica agendada se a janela ainda não abriu e ativa se já abriu — o "
              + "início é inclusivo.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Versão publicada",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SurveyVersionResponseDTO.class)),
        headers =
            @Header(
                name = "Location",
                description = "URI da versão publicada",
                schema = @Schema(implementation = String.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Classificação fora de cosmetic/semantic ou resumo acima de 500 caracteres",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiValidationErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Pesquisa não encontrada",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Pesquisa já publicada e sem rascunho de versão aberto",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "Rascunho não publicável (survey.not_publishable, com a extensão impediments); "
                + "versão idêntica à anterior (survey_version.no_changes); declaração de "
                + "cosmética contradita por diferença estrutural "
                + "(survey_version.cosmetic_refused, com a extensão differences)",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<SurveyVersionResponseDTO> publish(
      @Parameter(description = "Identificador da aplicação dona") String applicationId,
      @Parameter(description = "Identificador da pesquisa") String surveyId,
      PublishSurveyRequestDTO request);
}
