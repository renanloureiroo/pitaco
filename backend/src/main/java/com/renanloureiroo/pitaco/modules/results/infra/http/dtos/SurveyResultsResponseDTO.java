package com.renanloureiroo.pitaco.modules.results.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Resultado de uma pesquisa sob um recorte: taxa de resposta e agregados")
public record SurveyResultsResponseDTO(
    @Schema(description = "Falso enquanto a pesquisa nunca foi publicada: nada foi exibido")
        boolean everPublished,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResponseRateDTO responseRate,
    @Schema(description = "Uma entrada por pergunta, na ordem da pesquisa")
        List<QuestionResultDTO> questions,
    @Schema(description = "Exibições com ao menos uma resposta dada no recorte", example = "42")
        long sampleSize,
    @Schema(description = "Verdadeiro quando a amostra é pequena demais para proporção")
        boolean smallSample,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ResultsFilterDTO filter,
    @Schema(description = "Atributos já vistos nas exibições da pesquisa, com seus valores")
        List<AttributeCatalogDTO> attributes,
    @Schema(
            description =
                "NPS da pergunta do modelo, no topo. Presente só em pesquisa criada a partir do "
                    + "modelo de NPS",
            nullable = true)
        NpsSummaryDTO nps,
    @Schema(
            description =
                "Presente quando a retenção já descartou respostas desta pesquisa. Diz se o "
                    + "agregado congelado entrou na conta deste recorte",
            nullable = true)
        RetentionDTO retention) {

  @Schema(description = "O agregado das respostas descartadas pela política de retenção")
  public record RetentionDTO(
      @Schema(
              description =
                  "Verdadeiro quando o congelado foi somado aos números. Falso em recorte de "
                      + "período ou de atributo, que o congelado não sabe honrar")
          boolean snapshotApplied,
      @Schema(description = "As respostas descartadas foram dadas antes deste instante")
          Instant discardedBefore,
      @Schema(description = "Explicação para a tela, em português") String note) {}

  @Schema(description = "O NPS calculado da pergunta que o modelo pôs na pesquisa")
  public record NpsSummaryDTO(
      String questionKey,
      @Schema(description = "Quantos responderam a pergunta de NPS") long respondents,
      long promoters,
      long passives,
      long detractors,
      @Schema(description = "De -100 a 100. Ausente quando ninguém respondeu", nullable = true)
          Double score) {}

  @Schema(
      description =
          "Se somar as versões desta pergunta é seguro. Falso quando tipo, opções, faixa ou "
              + "condição mudaram entre as versões que receberam exibição no recorte")
  public record ComparabilityDTO(
      boolean comparable,
      @Schema(description = "Versões com exibição no recorte que têm esta pergunta")
          List<Integer> versions) {}

  @Schema(description = "Contagens de exibição e a taxa, com a definição do cálculo")
  public record ResponseRateDTO(
      long displayed,
      long completed,
      long dismissed,
      long abandoned,
      long inProgress,
      @Schema(description = "Concluídas ÷ exibidas, entre 0 e 1. Ausente sem exibição", nullable = true)
          Double rate,
      @Schema(description = "Como a taxa é calculada, em português") String definition,
      @Schema(description = "Exibidas e concluídas por dia de abertura, em UTC")
          List<TimelinePointDTO> timeline) {}

  public record TimelinePointDTO(LocalDate day, long displayed, long completed) {}

  @Schema(description = "Contagens e agregado de uma pergunta")
  public record QuestionResultDTO(
      String key,
      String statement,
      @Schema(
              description = "Tipo da pergunta, em minúsculas como na autoria",
              allowableValues = {
                "single_choice", "multiple_choice", "rating", "scale", "nps", "free_text"
              })
          String type,
      int position,
      long answered,
      long skipped,
      @Schema(
              description =
                  "Não aplicáveis: a condição tirou a pergunta do caminho. Diferente de puladas, e "
                      + "fora do denominador das proporções")
          long notApplicable,
      @Schema(description = "Ausente quando ninguém respondeu: sem resposta não é zero", nullable = true)
          QuestionAggregateDTO aggregate,
      @Schema(description = "Presente só no consolidado, sem versão no recorte", nullable = true)
          ComparabilityDTO comparability) {}

  @Schema(description = "Agregado na forma do tipo da pergunta; `kind` diz qual")
  public record QuestionAggregateDTO(
      @Schema(allowableValues = {"choice", "numeric", "nps", "text"}) String kind,
      List<OptionShareDTO> options,
      Double average,
      List<ValueShareDTO> distribution,
      Long promoters,
      Long passives,
      Long detractors,
      @Schema(description = "NPS de -100 a 100") Double score) {}

  public record OptionShareDTO(String value, String label, long count, double share) {}

  public record ValueShareDTO(int value, long count, double share) {}

  @Schema(description = "O recorte aplicado, ecoado para ficar visível junto dos números")
  public record ResultsFilterDTO(
      Instant from,
      Instant to,
      String attribute,
      String attributeValue,
      @Schema(description = "Verdadeiro quando o recorte é 'sem o atributo'") boolean attributeAbsent,
      Integer version) {}

  public record AttributeCatalogDTO(String name, List<AttributeValueCountDTO> values) {}

  public record AttributeValueCountDTO(String value, long count) {}
}
