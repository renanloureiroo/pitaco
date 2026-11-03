package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

// A pesquisa dos três E2E de resultados: NPS, escolha única, avaliação e texto livre, numa
// versão publicada, com um respondente por exibição semeada.
abstract class ResultsE2ESupport {

  static final String HEADER = "X-Pitaco-Key";
  static final Instant DAY_ONE = Instant.parse("2026-09-01T10:00:00Z");
  static final Instant DAY_TWO = Instant.parse("2026-09-02T10:00:00Z");

  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired SurveyDisplayRepository displays;
  @Autowired RespondentRepository respondents;
  @Autowired AnswerRepository answers;

  ApplicationId applicationId;
  SurveyId surveyId;
  SurveyVersionId versionId;
  QuestionKey nps;
  QuestionKey choice;
  QuestionKey rating;
  QuestionKey text;
  String base;

  private int respondentSequence;

  void seedSurvey(ApplicationFactory application) {
    var built = application.build();
    applications.save(ApplicationJpaMapper.toJpa(built));
    applicationId = built.id();

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    surveyId = survey.id();

    var version =
        SurveyVersionFactory.aVersion()
            .forSurvey(surveyId)
            .withQuestions(
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?"),
                QuestionFactory.aSingleChoiceQuestion().withStatement("Recomendaria?"),
                QuestionFactory.aRatingQuestion().withStatement("Avalie"),
                QuestionFactory.aFreeTextQuestion().withStatement("O que achou?"))
            .buildPublishedSavedIn(versions);
    versionId = version.id();
    var questions = version.getQuestions();
    nps = keyOf(questions, 1);
    choice = keyOf(questions, 2);
    rating = keyOf(questions, 3);
    text = keyOf(questions, 4);

    base = "/applications/" + applicationId.value() + "/surveys/" + surveyId.value() + "/results";
  }

  Application application() {
    return ApplicationJpaMapper.toDomain(applications.findById(applicationId.value()).orElseThrow());
  }

  private static QuestionKey keyOf(java.util.List<Question> questions, int position) {
    return questions.stream()
        .filter(question -> question.getPosition() == position)
        .findFirst()
        .orElseThrow()
        .getKey();
  }

  SurveyVersion anotherPublishedVersion(int number) {
    return SurveyVersionFactory.aVersion()
        .forSurvey(surveyId)
        .numbered(number)
        .withQuestions(QuestionFactory.anNpsQuestion().withStatement("Só o NPS, na v" + number))
        .buildPublishedSavedIn(versions);
  }

  RespondentId aRespondent() {
    return RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("u-" + (++respondentSequence))
        .buildSavedIn(respondents)
        .id();
  }

  DisplayId display(DisplayOutcome outcome, Instant openedAt, Map<String, String> attributes) {
    return displayOn(versionId, outcome, openedAt, attributes);
  }

  DisplayId displayOn(
      SurveyVersionId version, DisplayOutcome outcome, Instant openedAt, Map<String, String> attributes) {
    var factory =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(version)
            .forRespondent(aRespondent())
            .withAttributes(attributes)
            .openedAt(openedAt);

    var closed =
        switch (outcome) {
          case COMPLETED -> factory.completedAt(openedAt.plusSeconds(30));
          case DISMISSED -> factory.dismissedAt(openedAt.plusSeconds(10));
          case STARTED, ABANDONED -> factory;
        };

    return closed.buildSavedIn(displays).id();
  }

  void number(DisplayId displayId, QuestionKey key, int value) {
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(key).withNumber(value).buildSavedIn(answers);
  }

  void options(DisplayId displayId, QuestionKey key, String... values) {
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(key).withOptions(values).buildSavedIn(answers);
  }

  void text(DisplayId displayId, QuestionKey key, String value, Instant answeredAt) {
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(key)
        .withText(value)
        .answeredAt(answeredAt)
        .buildSavedIn(answers);
  }

  void skipped(DisplayId displayId, QuestionKey key) {
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(key).skipped().buildSavedIn(answers);
  }
}
