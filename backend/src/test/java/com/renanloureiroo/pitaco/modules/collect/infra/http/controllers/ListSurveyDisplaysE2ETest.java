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
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplaySummaryResponseDTO;
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
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/displays")
class ListSurveyDisplaysE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final Instant FIRST = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-08T11:00:00Z");
  private static final Instant THIRD = Instant.parse("2026-09-08T12:00:00Z");

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired SurveyDisplayRepository displays;
  @Autowired RespondentRepository respondents;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;
  private RespondentId respondentId;
  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    surveyId = survey.id();

    versionId =
        SurveyVersionFactory.aVersion().forSurvey(surveyId).buildPublishedSavedIn(versions).id();

    respondentId =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .buildSavedIn(respondents)
            .id();

    uri = "/applications/" + applicationId.value() + "/surveys/" + surveyId.value() + "/displays";
  }

  private DisplayId display(Instant openedAt, DisplayOutcome outcome) {
    var factory =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .forRespondent(respondentId)
            .openedAt(openedAt);

    var closed =
        switch (outcome) {
          case COMPLETED -> factory.completedAt(openedAt.plusSeconds(30));
          case DISMISSED -> factory.dismissedAt(openedAt.plusSeconds(10));
          case STARTED, ABANDONED -> factory;
        };

    return closed.buildSavedIn(displays).id();
  }

  private PageResponseDTO<DisplaySummaryResponseDTO> list(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<DisplaySummaryResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
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

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  @DisplayName("Devolve os três desfechos, e cada linha bate com o que está gravado no banco")
  void caminho_feliz() {
    var completed = display(THIRD, DisplayOutcome.COMPLETED);
    var dismissed = display(SECOND, DisplayOutcome.DISMISSED);
    var started = display(FIRST, DisplayOutcome.STARTED);

    var page = list("");

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(completed.value(), dismissed.value(), started.value());
    assertThat(page.items())
        .extracting(DisplaySummaryResponseDTO::outcome)
        .containsExactly(
            DisplayOutcome.COMPLETED, DisplayOutcome.DISMISSED, DisplayOutcome.STARTED);

    var first = page.items().get(0);
    assertThat(first.versionId()).isEqualTo(versionId.value());
    assertThat(first.versionNumber()).isEqualTo(1);
    assertThat(first.sdkVersion()).isEqualTo("1.4.2");
    assertThat(first.openedAt()).isEqualTo(storedInstant(completed, "opened_at"));
    assertThat(first.closedAt()).isEqualTo(storedInstant(completed, "closed_at"));
    assertThat(page.items().get(2).closedAt()).isNull();
    assertThat(storedInstant(started, "closed_at")).isNull();
  }

  private Instant storedInstant(DisplayId id, String column) {
    return jdbc
        .sql("select " + column + " from survey_displays where id = :id")
        .param("id", id.value())
        .query(Instant.class)
        .optional()
        .orElse(null);
  }

  @Test
  @DisplayName("Pesquisa que existe e nunca foi exibida devolve página vazia, não 404")
  void pesquisa_sem_exibicao() {
    var page = list("");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Filtra pelo número da versão, por desfecho e por período inclusivo nos extremos")
  void filtra() {
    var otherVersion = versionNumbered(2);
    var completed = display(THIRD, DisplayOutcome.COMPLETED);
    var dismissed = display(SECOND, DisplayOutcome.DISMISSED);
    var onOtherVersion = displayOnVersion(otherVersion, FIRST);

    assertThat(list("?versionNumber=2").items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(onOtherVersion.value());
    assertThat(list("?versionNumber=1").items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(completed.value(), dismissed.value());
    assertThat(list("?outcome=DISMISSED").items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(dismissed.value());
    assertThat(list("?openedFrom=" + SECOND + "&openedTo=" + SECOND).items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(dismissed.value());
    assertThat(list("?openedFrom=" + FIRST + "&openedTo=" + THIRD).items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(completed.value(), dismissed.value(), onOtherVersion.value());

    var beyond = list("?openedFrom=" + THIRD.plusSeconds(1));
    assertThat(beyond.items()).isEmpty();
    assertThat(beyond.total()).isZero();
  }

  private SurveyVersionId versionNumbered(int number) {
    return SurveyVersionFactory.aVersion()
        .forSurvey(surveyId)
        .numbered(number)
        .buildPublishedSavedIn(versions)
        .id();
  }

  private DisplayId displayOnVersion(SurveyVersionId version, Instant openedAt) {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(version)
        .forRespondent(respondentId)
        .openedAt(openedAt)
        .buildSavedIn(displays)
        .id();
  }

  @Test
  @DisplayName("Número de versão que a pesquisa não tem devolve página vazia, não 404")
  void filtra_por_numero_de_versao_inexistente() {
    display(FIRST, DisplayOutcome.STARTED);

    var page = list("?versionNumber=99");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Com filtro de versão, o total é o do conjunto inteiro, não o da página")
  void total_com_filtro_de_versao_conta_o_conjunto_sem_paginacao() {
    var second = versionNumbered(2);
    displayOnVersion(second, FIRST);
    displayOnVersion(second, SECOND);
    displayOnVersion(second, THIRD);
    display(FIRST, DisplayOutcome.STARTED);

    var page = list("?versionNumber=2&size=1");

    assertThat(page.items()).hasSize(1);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(list("?versionNumber=2&size=100").items()).hasSize(3);
  }

  @Test
  @DisplayName("Sem filtro de versão, devolve as exibições de todas as versões")
  void sem_filtro_de_versao_devolve_todas() {
    var second = versionNumbered(2);
    var onFirst = display(SECOND, DisplayOutcome.COMPLETED);
    var onSecond = displayOnVersion(second, THIRD);

    var page = list("");

    assertThat(page.items())
        .extracting(DisplaySummaryResponseDTO::id)
        .containsExactly(onSecond.value(), onFirst.value());
    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Página além do fim devolve vazio com o total correto")
  void pagina_alem_do_fim() {
    display(FIRST, DisplayOutcome.STARTED);
    display(SECOND, DisplayOutcome.COMPLETED);

    var page = list("?page=9&size=2");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Paginação, desfecho desconhecido e período invertido respondem 400")
  void recusa_parametros_invalidos() {
    expectBadRequest("?page=-1");
    expectBadRequest("?size=0");
    expectBadRequest("?size=101");
    expectBadRequest("?outcome=ABANDONED");
    expectBadRequest("?outcome=qualquer");
    expectBadRequest("?versionNumber=nao-e-um-numero");
    expectBadRequest("?versionNumber=0");
    expectBadRequest("?versionNumber=-1");
    expectBadRequest("?openedFrom=amanha");
    expectBadRequest("?openedFrom=" + THIRD + "&openedTo=" + FIRST);
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
  @DisplayName("Pesquisa inexistente ou de outra aplicação responde 404, nunca 403")
  void recusa_pesquisa_fora_do_escopo() {
    var otherApplication = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(ApplicationJpaMapper.toJpa(otherApplication));
    var alheia =
        SurveyFactory.aSurvey().forApplication(otherApplication.id()).buildSavedIn(surveys);

    expectNotFound(
        "/applications/"
            + applicationId.value()
            + "/surveys/"
            + UUID.randomUUID()
            + "/displays");
    expectNotFound(
        "/applications/"
            + applicationId.value()
            + "/surveys/"
            + alheia.id().value()
            + "/displays");
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
        .isEqualTo("survey.not_found");
  }

  @Test
  @DisplayName("Nenhum caminho, feliz ou de recusa, altera o estado gravado")
  void nao_altera_o_estado() {
    display(FIRST, DisplayOutcome.STARTED);
    display(SECOND, DisplayOutcome.COMPLETED);

    list("");
    list("?outcome=COMPLETED");
    client.get().uri(uri + "?size=0").exchange().expectStatus().isBadRequest();
    client.get().uri(uri).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri("/applications/" + applicationId.value() + "/surveys/" + UUID.randomUUID() + "/displays")
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(countOf("survey_displays")).isEqualTo(2);
    assertThat(countOf("respondents")).isEqualTo(1);
    assertThat(countOf("survey_answers")).isZero();
    assertThat(
            jdbc.sql("select count(*) from survey_displays where outcome = 'COMPLETED'")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }
}
