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

  record SurveyCandidate(
      SurveyId surveyId,
      SurveyVersionId versionId,
      int versionNumber,
      int comparabilityGroup,
      SamplingRate rate,
      List<SegmentationCriterion> criteria,
      Instant publishedAt) {}

  record PublishedVersion(
      SurveyId surveyId, SurveyVersionId versionId, int versionNumber, int comparabilityGroup) {}

  record DeliverableSurvey(
      SurveyId surveyId,
      SurveyVersionId versionId,
      int versionNumber,
      List<DeliverableQuestion> questions) {}

  record DeliverableQuestion(
      QuestionKey key,
      int position,
      String statement,
      QuestionType type,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {}
}
