package com.renanloureiroo.pitaco.modules.privacy.infra.http.controllers;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// Uma pesquisa publicada com NPS e texto livre, e respondentes que a respondem. É o que os E2E
// de privacidade apagam, descartam e leem de volta.
abstract class PrivacyE2ESupport {

  static final String HEADER = "X-Pitaco-Key";

  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired SurveyDisplayRepository displays;
  @Autowired RespondentRepository respondents;
  @Autowired AnswerRepository answers;
  @Autowired JdbcClient jdbc;

  record SeededSurvey(SurveyId surveyId, SurveyVersionId versionId, QuestionKey nps, QuestionKey text) {}

  ApplicationId seedApplication() {
    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    return application.id();
  }

  void retain(ApplicationId applicationId, Integer answerDays, Integer textDays) {
    jdbc.sql(
            "update applications set retention_days = :answers, open_text_retention_days = :texts"
                + " where id = :id")
        .param("answers", answerDays)
        .param("texts", textDays)
        .param("id", applicationId.value())
        .update();
  }

  SeededSurvey seedSurvey(ApplicationId applicationId) {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);

    var version =
        SurveyVersionFactory.aVersion()
            .forSurvey(survey.id())
            .withQuestions(
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?"),
                QuestionFactory.aFreeTextQuestion().withStatement("O que achou?"))
            .buildPublishedSavedIn(versions);

    return new SeededSurvey(
        survey.id(), version.id(), keyOf(version.getQuestions(), 1), keyOf(version.getQuestions(), 2));
  }

  private static QuestionKey keyOf(List<Question> questions, int position) {
    return questions.stream()
        .filter(question -> question.getPosition() == position)
        .findFirst()
        .orElseThrow()
        .getKey();
  }

  RespondentId respondentByReference(ApplicationId applicationId, String reference) {
    return RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference(reference)
        .buildSavedIn(respondents)
        .id();
  }

  RespondentId respondentByDevice(ApplicationId applicationId, String deviceId) {
    return RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByDevice(deviceId)
        .buildSavedIn(respondents)
        .id();
  }

  // Exibição concluída com as duas perguntas respondidas no instante dado.
  DisplayId answered(
      ApplicationId applicationId,
      SeededSurvey survey,
      RespondentId respondent,
      Instant answeredAt,
      int score,
      String text) {
    var display =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(survey.surveyId())
            .forVersion(survey.versionId())
            .forRespondent(respondent)
            .withAttributes(Map.of("plano", "pro"))
            .openedAt(answeredAt.minusSeconds(30))
            .completedAt(answeredAt)
            .buildSavedIn(displays)
            .id();

    AnswerFactory.anAnswer()
        .forDisplay(display)
        .forQuestion(survey.nps())
        .withNumber(score)
        .answeredAt(answeredAt)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(display)
        .forQuestion(survey.text())
        .withText(text)
        .answeredAt(answeredAt)
        .buildSavedIn(answers);

    return display;
  }

  long count(String sql, Object... params) {
    var statement = jdbc.sql(sql);
    for (var param : params) {
      statement = statement.param(param);
    }
    return statement.query(Long.class).single();
  }

  String base(ApplicationId applicationId) {
    return "/applications/" + applicationId.value();
  }
}
