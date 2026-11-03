package com.renanloureiroo.pitaco.modules.results.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.ResultsFilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Comportamento dentro da pesquisa, lido dos eventos de interação do SDK sob o mesmo "
            + "recorte dos resultados. Cada número tem a definição em `definitions`")
public record SurveyBehaviorResponseDTO(
    @Schema(description = "Falso enquanto a pesquisa nunca foi publicada: nada foi exibido")
        boolean everPublished,
    @Schema(description = "Exibições no recorte, com ou sem eventos", example = "120")
        long displayed,
    @Schema(
            description = "Exibições no recorte com ao menos um evento aceito: a base da leitura",
            example = "96")
        long instrumented,
    @Schema(description = "Uma entrada por pergunta publicada, na ordem da pesquisa")
        List<QuestionBehaviorDTO> questions,
    DismissalsDTO dismissals,
    ResultsFilterDTO filter,
    @Schema(description = "A definição de cada métrica, em português, para a tela exibir")
        List<MetricDefinitionDTO> definitions) {

  @Schema(description = "Funil, tempo e hesitação de uma pergunta")
  public record QuestionBehaviorDTO(
      String key,
      String statement,
      @Schema(example = "nps") String type,
      int position,
      @Schema(description = "Exibições em que a pergunta foi vista") long viewed,
      @Schema(description = "Exibições cuja última saída da pergunta foi com resposta") long answered,
      @Schema(description = "Exibições cuja última saída foi sem resposta, seguindo adiante")
          long skipped,
      @Schema(description = "Exibições dispensadas ou abandonadas com esta como última vista")
          long abandoned,
      ActiveTimeDTO activeTime,
      @Schema(description = "Exibições que voltaram à pergunta depois de sair") long revisited,
      @Schema(description = "revisited ÷ viewed, de 0 a 1. Ausente sem vista", nullable = true)
          Double revisitRate,
      @Schema(description = "Exibições com ao menos uma escolha na pergunta") long selected,
      @Schema(description = "Exibições que trocaram ou desmarcaram a escolha") long changed,
      @Schema(description = "changed ÷ selected, de 0 a 1. Ausente sem escolha", nullable = true)
          Double answerChangeRate,
      @Schema(description = "Exibições com ao menos um bloqueio de validação")
          long validationBlockedDisplays,
      @Schema(description = "Total de bloqueios de validação") long validationBlocks) {}

  @Schema(description = "Tempo ativo por exibição, somadas as visitas, em milissegundos")
  public record ActiveTimeDTO(
      @Schema(description = "Exibições que saíram da pergunta ao menos uma vez") long samples,
      @Schema(description = "Mediana. Ausente sem amostra", nullable = true) Long medianMs,
      @Schema(description = "Percentil 90. Ausente sem amostra", nullable = true) Long p90Ms) {}

  @Schema(description = "Dispensas por via, a da última dispensa de cada exibição")
  public record DismissalsDTO(
      long total,
      @Schema(description = "As seis vias do catálogo, sempre presentes") List<ViaCountDTO> byVia,
      @Schema(description = "Dispensas sem via reconhecida") long unspecified) {}

  public record ViaCountDTO(
      @Schema(
              allowableValues = {
                "close_button",
                "swipe",
                "backdrop",
                "hardware_back",
                "navigation",
                "programmatic"
              })
          String via,
      long count,
      @Schema(description = "count ÷ total, de 0 a 1. Ausente sem dispensa", nullable = true)
          Double share) {}

  public record MetricDefinitionDTO(
      @Schema(example = "revisitRate") String metric, String definition) {}
}
