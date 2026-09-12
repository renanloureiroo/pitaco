package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;

// O preparo comum aos E2E de saúde: aplicação com chave e pesquisa publicada ouvindo um evento.
final class HealthE2ESupport {

  static final String KEY_HEADER = "X-Pitaco-Key";
  static final String VERSION_HEADER = "X-Pitaco-Sdk-Version";
  static final String EVENT = "checkout.completed";

  private HealthE2ESupport() {}

  record Tenant(ApplicationId applicationId, String key) {}

  static Tenant tenant(
      ApplicationJpaRepository applications, ApiKeyJpaRepository apiKeys, String slug) {
    var application = ApplicationFactory.anApplication().withSlug(slug).build();
    applications.save(ApplicationJpaMapper.toJpa(application));

    var issued = ApiKey.issue(application.id(), ApiKeyLabel.of("app " + slug));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));

    return new Tenant(application.id(), issued.plainSecret());
  }

  static SurveyVersion publishedSurvey(
      SurveyRepository surveys, SurveyVersionRepository versions, ApplicationId owner) {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(owner)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);

    return SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(TriggerFactory.aTrigger().forEvent(EVENT).withRate(1.0))
        .buildPublishedSavedIn(versions);
  }
}
