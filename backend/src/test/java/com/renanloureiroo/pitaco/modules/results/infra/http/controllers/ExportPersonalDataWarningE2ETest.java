package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET .../results/export — aviso de dado pessoal")
class ExportPersonalDataWarningE2ETest extends ResultsE2ESupport {

  private static final String WARNING = "X-Pitaco-Content-Warning";

  @Autowired RestTestClient client;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
    seedSurvey(ApplicationFactory.anApplication());
  }

  @Test
  @DisplayName("Pesquisa com texto livre traz o cabeçalho de que pode haver dado pessoal")
  void com_texto_livre() {
    client
        .get()
        .uri(base + "/export")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(WARNING, "may-contain-personal-data");
  }

  @Test
  @DisplayName("Pesquisa sem texto livre não traz o cabeçalho")
  void sem_texto_livre() {
    var other =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(other.id())
        .withQuestions(QuestionFactory.anNpsQuestion())
        .buildPublishedSavedIn(versions);

    var result =
        client
            .get()
            .uri("/applications/" + applicationId.value() + "/surveys/" + other.id().value() + "/results/export")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getResponseHeaders().containsHeader(WARNING)).isFalse();
  }
}
