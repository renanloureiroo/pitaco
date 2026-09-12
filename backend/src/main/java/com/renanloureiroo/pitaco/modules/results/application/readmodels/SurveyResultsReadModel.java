package com.renanloureiroo.pitaco.modules.results.application.readmodels;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.core.pagination.PageQuery;
import com.renanloureiroo.pitaco.modules.results.domain.comparability.QuestionShape;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Modelo de leitura em SQL, sem passar pelo domínio da coleta: cada método é uma consulta
// agrupada no banco, nunca uma varredura em memória nem uma chamada por linha.
public interface SurveyResultsReadModel {

  // Perguntas das versões publicadas. Sem versão, consolida: uma linha por chave estável, com a
  // definição da versão mais recente que a tem.
  List<QuestionDefinition> questionsOf(SurveyId surveyId, Optional<Integer> versionNumber);

  DisplayCounts displayCountsOf(ResultsFilter filter, Instant abandonedBefore);

  List<DailyCounts> timelineOf(ResultsFilter filter);

  List<AnswerCounts> answerCountsOf(ResultsFilter filter);

  // Uma forma por pergunta de cada versão publicada: é o que decide se somar as versões engana.
  List<QuestionShape> questionShapesOf(SurveyId surveyId);

  // Versões com ao menos uma exibição no recorte. Só elas entram na soma do consolidado.
  Set<Integer> displayedVersionsOf(ResultsFilter filter);

  List<OptionCount> optionCountsOf(ResultsFilter filter);

  List<NumericCount> numericCountsOf(ResultsFilter filter);

  // Catálogo do recorte por atributo: o que já apareceu nas exibições da pesquisa, sem filtro.
  List<AttributeValueCount> attributeCatalogOf(ApplicationId applicationId, SurveyId surveyId);

  Page<OpenAnswerRow> openAnswersOf(OpenAnswersQuery query);

  List<AnswerRow> answersOf(List<String> displayIds);

  List<String> attributeNamesOf(ResultsFilter filter);

  // Lote em ordem de abertura com desempate pelo identificador; `after` é a última linha do
  // lote anterior. É o que deixa exportar sem carregar a pesquisa inteira na memória.
  List<DisplayRow> displaysAfter(ResultsFilter filter, Optional<DisplayCursor> after, int limit);

  List<AttributeRow> attributesOf(List<String> displayIds);

  // O que a retenção congelou antes de apagar as respostas, somado por pergunta, dimensão e
  // valor. Só honra o recorte de versão: o congelado não guarda data nem atributo.
  List<RetainedCount> retainedCountsOf(SurveyId surveyId, Optional<Integer> versionNumber);

  Optional<RetainedSummary> retainedSummaryOf(SurveyId surveyId, Optional<Integer> versionNumber);

  // dimension é STATUS (valor ANSWERED, SKIPPED ou NOT_APPLICABLE), OPTION ou NUMBER.
  record RetainedCount(QuestionKey key, String dimension, String value, long count) {}

  record RetainedSummary(long respondingDisplays, Instant discardedBefore) {}

  record ResultsFilter(
      ApplicationId applicationId,
      SurveyId surveyId,
      Optional<Instant> from,
      Optional<Instant> to,
      Optional<AttributeFilter> attribute,
      Optional<Integer> versionNumber) {}

  // Valor ausente é o recorte "sem o atributo": quem não o enviou forma um grupo próprio, em vez
  // de sumir da conta em silêncio.
  record AttributeFilter(String name, Optional<String> value) {}

  record QuestionDefinition(
      QuestionKey key,
      String statement,
      QuestionType type,
      int position,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {}

  record DisplayCounts(
      long displayed,
      long completed,
      long dismissed,
      long abandoned,
      long inProgress,
      long responding) {}

  record DailyCounts(LocalDate day, long displayed, long completed) {}

  record AnswerCounts(QuestionKey key, long answered, long skipped, long notApplicable) {

    public AnswerCounts(QuestionKey key, long answered, long skipped) {
      this(key, answered, skipped, 0);
    }
  }

  record OptionCount(QuestionKey key, String value, long count) {}

  record NumericCount(QuestionKey key, int value, long count) {}

  record AttributeValueCount(String name, String value, long count) {}

  record OpenAnswersQuery(
      ResultsFilter filter, Optional<String> term, Optional<Instant> notBefore, int page, int size)
      implements PageQuery {}

  record OpenAnswerRow(String displayId, QuestionKey key, String text, Instant answeredAt) {}

  record AnswerRow(
      String displayId,
      QuestionKey key,
      String status,
      Optional<String> text,
      Optional<Integer> number,
      List<String> options,
      Instant answeredAt) {}

  record DisplayRow(
      String id,
      String respondentReference,
      int versionNumber,
      String storedOutcome,
      Optional<String> sdkVersion,
      Instant openedAt,
      Optional<Instant> closedAt) {}

  record DisplayCursor(Instant openedAt, String id) {}

  record AttributeRow(String displayId, String name, String value) {}
}
