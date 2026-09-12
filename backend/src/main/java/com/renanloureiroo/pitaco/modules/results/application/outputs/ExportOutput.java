package com.renanloureiroo.pitaco.modules.results.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.results.domain.DisplayResolution;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

// O cabeçalho sai resolvido antes de qualquer linha, e as linhas chegam por um consumidor: é o
// que deixa a borda começar a escrever a resposta sem esperar o lote inteiro.
public record ExportOutput(
    List<String> attributeNames,
    List<ExportQuestion> questions,
    ExportRows rows,
    boolean mayContainPersonalData) {

  public ExportOutput(List<String> attributeNames, List<ExportQuestion> questions, ExportRows rows) {
    this(attributeNames, questions, rows, false);
  }

  public record ExportQuestion(QuestionKey key, String statement) {}

  @FunctionalInterface
  public interface ExportRows {
    void forEach(Consumer<ExportRow> consumer);
  }

  public record ExportRow(
      String displayId,
      String respondentReference,
      int versionNumber,
      DisplayResolution outcome,
      Instant openedAt,
      Optional<Instant> closedAt,
      Optional<String> sdkVersion,
      Map<String, String> attributes,
      Map<QuestionKey, ExportAnswer> answers) {}

  // Texto vencido pela retenção e pergunta pulada chegam iguais: célula vazia. Não aplicável tem
  // marca própria, porque "a pessoa nunca viu a pergunta" não pode se confundir com "pulou".
  public record ExportAnswer(
      Optional<String> text, Optional<Integer> number, List<String> options, boolean notApplicable) {

    public static final ExportAnswer EMPTY =
        new ExportAnswer(Optional.empty(), Optional.empty(), List.of());

    public static final ExportAnswer NOT_APPLICABLE =
        new ExportAnswer(Optional.empty(), Optional.empty(), List.of(), true);

    public ExportAnswer(Optional<String> text, Optional<Integer> number, List<String> options) {
      this(text, number, options, false);
    }
  }
}
