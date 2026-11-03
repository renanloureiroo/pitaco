package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDisplayResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/respondents/{respondentId}/displays")
class ListRespondentDisplaysE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final Instant FIRST = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-08T11:00:00Z");

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired SurveyDisplayRepository displays;
  @Autowired RespondentRepository respondents;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private RespondentId respondentId;
  private SurveyId firstSurvey;
  private SurveyVersionId firstVersion;
  private SurveyId secondSurvey;
  private SurveyVersionId secondVersion;
  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    firstSurvey = aPublishedSurvey("Primeira");
    firstVersion = aPublishedVersionOf(firstSurvey);
    secondSurvey = aPublishedSurvey("Segunda");
    secondVersion = aPublishedVersionOf(secondSurvey);

    respondentId =
        RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents).id();
    uri = "/applications/" + applicationId.value() + "/respondents/" + respondentId.value() + "/displays";
  }

  private SurveyId aPublishedSurvey(String name) {
    return SurveyFactory.aSurvey()
        .forApplication(applicationId)
        .withName(name)
        .published(1)
        .inLifecycle(SurveyLifecycle.PUBLISHED)
        .buildSavedIn(surveys)
        .id();
  }

  private SurveyVersionId aPublishedVersionOf(SurveyId surveyId) {
    return SurveyVersionFactory.aVersion().forSurvey(surveyId).buildPublishedSavedIn(versions).id();
  }

  private DisplayId display(
      SurveyId surveyId, SurveyVersionId versionId, Instant openedAt, DisplayOutcome outcome) {
    var factory =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forRespondent(respondentId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .openedAt(openedAt);

    var closed =
        switch (outcome) {
          case COMPLETED -> factory.completedAt(openedAt.plusSeconds(30));
          case DISMISSED -> factory.dismissedAt(openedAt.plusSeconds(10));
          case STARTED, ABANDONED -> factory;
        };

    return closed.buildSavedIn(displays).id();
  }

  private PageResponseDTO<RespondentDisplayResponseDTO> list(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(
            new ParameterizedTypeReference<PageResponseDTO<RespondentDisplayResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  @DisplayName("Exibições de duas pesquisas aparecem apontando pesquisa e versão")
  void caminho_feliz() {
    var older = display(firstSurvey, firstVersion, FIRST, DisplayOutcome.COMPLETED);
    var newer = display(secondSurvey, secondVersion, SECOND, DisplayOutcome.STARTED);

    var page = list("");

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items())
        .extracting(RespondentDisplayResponseDTO::id)
        .containsExactly(newer.value(), older.value());
    assertThat(page.items())
        .extracting(RespondentDisplayResponseDTO::surveyId)
        .containsExactly(secondSurvey.value(), firstSurvey.value());

    var first = page.items().get(0);
    assertThat(first.versionId()).isEqualTo(secondVersion.value());
    assertThat(first.versionNumber()).isEqualTo(1);
    assertThat(first.outcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(first.closedAt()).isNull();
    assertThat(page.items().get(1).closedAt()).isEqualTo(storedClosedAt(older));
  }

  private Instant storedClosedAt(DisplayId id) {
    return jdbc
        .sql("select closed_at from survey_displays where id = :id")
        .param("id", id.value())
        .query(Instant.class)
        .single();
  }

  @Test
  @DisplayName("Respondente sem nenhuma exibição devolve página vazia com total 0")
  void respondente_sem_exibicao() {
    var page = list("");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Filtra por desfecho e por período inclusivo nos extremos")
  void filtra() {
    var dismissed = display(firstSurvey, firstVersion, FIRST, DisplayOutcome.DISMISSED);
    var started = display(secondSurvey, secondVersion, SECOND, DisplayOutcome.STARTED);

    assertThat(list("?outcome=DISMISSED").items())
        .extracting(RespondentDisplayResponseDTO::id)
        .containsExactly(dismissed.value());
    assertThat(list("?openedFrom=" + SECOND + "&openedTo=" + SECOND).items())
        .extracting(RespondentDisplayResponseDTO::id)
        .containsExactly(started.value());

    var beyond = list("?openedFrom=" + SECOND.plusSeconds(1));
    assertThat(beyond.items()).isEmpty();
    assertThat(beyond.total()).isZero();
  }

  @Test
  @DisplayName("Não devolve exibição de outro respondente nem de outra aplicação")
  void isola_o_escopo() {
    var mine = display(firstSurvey, firstVersion, FIRST, DisplayOutcome.STARTED);
    var outro =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByDevice("outro")
            .buildSavedIn(respondents);
    SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forRespondent(outro.id())
        .forSurvey(firstSurvey)
        .forVersion(firstVersion)
        .openedAt(SECOND)
        .buildSavedIn(displays);

    var page = list("");

    assertThat(page.items())
        .extracting(RespondentDisplayResponseDTO::id)
        .containsExactly(mine.value());
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Paginação, desfecho desconhecido e período invertido respondem 400")
  void recusa_parametros_invalidos() {
    expectBadRequest("?page=-1");
    expectBadRequest("?size=101");
    expectBadRequest("?outcome=ABANDONED");
    expectBadRequest("?openedFrom=" + SECOND + "&openedTo=" + FIRST);
  }

  private void expectBadRequest(String query) {
    client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  @DisplayName("Chave de aplicação na superfície administrativa responde 403")
  void recusa_chave_de_aplicacao() {
    client
        .get()
        .uri(uri)
        .header(HEADER, "pit_qualquer")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");
  }

  @Test
  @DisplayName("Respondente inexistente ou de outra aplicação responde 404, nunca 403")
  void recusa_respondente_fora_do_escopo() {
    var outra = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));
    var alheio = RespondentFactory.aRespondent().forApplication(outra.id()).buildSavedIn(respondents);

    expectNotFound(
        "/applications/" + applicationId.value() + "/respondents/" + UUID.randomUUID() + "/displays");
    expectNotFound(
        "/applications/"
            + applicationId.value()
            + "/respondents/"
            + alheio.id().value()
            + "/displays");
    expectNotFound("/applications/" + applicationId.value() + "/respondents/nao-e-um-id/displays");
  }

  private void expectNotFound(String target) {
    client
        .get()
        .uri(target)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("respondent.not_found");
  }

  @Test
  @DisplayName("Nenhum caminho, feliz ou de recusa, altera o estado gravado")
  void nao_altera_o_estado() {
    display(firstSurvey, firstVersion, FIRST, DisplayOutcome.COMPLETED);
    display(secondSurvey, secondVersion, SECOND, DisplayOutcome.STARTED);

    list("");
    list("?outcome=STARTED");
    client.get().uri(uri + "?size=0").exchange().expectStatus().isBadRequest();
    client.get().uri(uri).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri(
            "/applications/"
                + applicationId.value()
                + "/respondents/"
                + UUID.randomUUID()
                + "/displays")
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(countOf("survey_displays")).isEqualTo(2);
    assertThat(countOf("respondents")).isEqualTo(1);
    assertThat(countOf("survey_answers")).isZero();
  }
}
