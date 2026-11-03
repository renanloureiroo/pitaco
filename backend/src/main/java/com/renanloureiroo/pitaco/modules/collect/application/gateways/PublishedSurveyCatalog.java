package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.catalog.SegmentationCriterion;
import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// A travessia até a autoria: coleta lê a versão publicada e nunca a modifica.
public interface PublishedSurveyCatalog {

  // Camadas 2 a 4 da FR-011 resolvidas no banco, com os critérios de segmentação na mesma
  // travessia — nada é buscado em laço.
  List<SurveyCandidate> candidatesFor(ApplicationId applicationId, EventName event, Instant now);

  Optional<DeliverableSurvey> contentOf(SurveyVersionId versionId);

  Optional<PublishedVersion> publishedVersionOf(
      SurveyVersionId versionId, ApplicationId applicationId);

  // A versão publicada corrente e o evento que ela escuta, independentemente de a pesquisa estar
  // no ar: a saúde é lida também de pesquisa pausada ou encerrada.
  Optional<CurrentPublication> currentPublicationOf(SurveyId surveyId);

  record CurrentPublication(SurveyVersionId versionId, Optional<EventName> event) {}

  record SurveyCandidate(
      SurveyId surveyId,
      SurveyVersionId versionId,
      int versionNumber,
      int comparabilityGroup,
      SamplingRate rate,
      List<SegmentationCriterion> criteria,
      Instant publishedAt,
      int priority,
      boolean ignoresQuietPeriod) {}

  record PublishedVersion(
      SurveyId surveyId, SurveyVersionId versionId, int versionNumber, int comparabilityGroup) {}

  // O aviso de texto livre é da pesquisa e viaja com a versão entregue, com o texto já resolvido:
  // o SDK não conhece o texto padrão. Ausente quando a pesquisa o desligou.
  record DeliverableSurvey(
      SurveyId surveyId,
      SurveyVersionId versionId,
      int versionNumber,
      List<DeliverableQuestion> questions,
      Optional<String> freeTextNotice) {

    public DeliverableSurvey {
      freeTextNotice = freeTextNotice == null ? Optional.empty() : freeTextNotice;
    }

    public DeliverableSurvey(
        SurveyId surveyId,
        SurveyVersionId versionId,
        int versionNumber,
        List<DeliverableQuestion> questions) {
      this(surveyId, versionId, versionNumber, questions, Optional.empty());
    }

    public boolean hasFreeTextNotice() {
      return freeTextNotice.isPresent()
          && questions.stream().anyMatch(question -> question.type() == QuestionType.FREE_TEXT);
    }
  }

  record DeliverableQuestion(
      QuestionKey key,
      int position,
      String statement,
      QuestionType type,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range,
      Optional<String> minLabel,
      Optional<String> maxLabel,
      Optional<DeliverableCondition> condition) {

    public DeliverableQuestion {
      minLabel = minLabel == null ? Optional.empty() : minLabel;
      maxLabel = maxLabel == null ? Optional.empty() : maxLabel;
      condition = condition == null ? Optional.empty() : condition;
    }

    public DeliverableQuestion(
        QuestionKey key,
        int position,
        String statement,
        QuestionType type,
        boolean required,
        List<QuestionOption> options,
        Optional<ScaleRange> range) {
      this(
          key,
          position,
          statement,
          type,
          required,
          options,
          range,
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    public boolean isConditional() {
      return condition.isPresent();
    }
  }

  // Quem avalia é o SDK, enquanto o respondente percorre a pesquisa. Aqui a condição só viaja, e a
  // coleta só precisa saber que ela existe para aceitar o "não aplicável".
  record DeliverableCondition(
      QuestionKey sourceKey,
      String operator,
      List<String> values,
      Optional<Integer> min,
      Optional<Integer> max) {

    public DeliverableCondition {
      values = List.copyOf(values);
    }
  }
}
