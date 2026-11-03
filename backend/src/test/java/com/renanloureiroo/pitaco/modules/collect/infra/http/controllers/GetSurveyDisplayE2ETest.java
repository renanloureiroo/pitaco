package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadStatus;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.AnswerReadResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplayDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/displays/{displayId}")
class GetSurveyDisplayE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final Instant OPENED_AT = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant LONG_AGO = Instant.parse("2020-01-01T10:00:00Z");
  private static final int RETENTION_DAYS = 30;

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired SurveyDisplayRepository displays;
  @Autowired RespondentRepository respondents;
  @Autowired AnswerRepository answers;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;
  private RespondentId respondentId;
  private List<QuestionKey> keys;

  @BeforeEach
  void setUp() {
    database.clean();

    var application =
        ApplicationFactory.anApplication().withOpenTextRetention(RETENTION_DAYS).build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

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
                QuestionFactory.aFreeTextQuestion().withStatement("O que dá para melhorar?"),
                QuestionFactory.aMultipleChoiceQuestion().withStatement("O que você usa?"),
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?").optional(),
                QuestionFactory.aFreeTextQuestion().withStatement("Podemos falar contigo?").optional())
            .buildPublishedSavedIn(versions);
    versionId = version.id();
    keys = keysOf(version);

    respondentId =
        RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents).id();
  }

  private static List<QuestionKey> keysOf(SurveyVersion version) {
    return version.getQuestions().stream()
        .sorted(Comparator.comparingInt(question -> question.getPosition()))
        .map(question -> question.getKey())
        .toList();
  }

  private String uriOf(DisplayId displayId) {
    return "/applications/" + applicationId.value() + "/displays/" + displayId.value();
  }

  private DisplayId aCompletedDisplay() {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(versionId)
        .forRespondent(respondentId)
        .withAttributes(Map.of("plan", "pro", "locale", "pt-BR"))
        .openedAt(OPENED_AT)
        .completedAt(OPENED_AT.plusSeconds(60))
        .buildSavedIn(displays)
        .id();
  }

  private DisplayDetailResponseDTO get(DisplayId displayId) {
    return client
        .get()
        .uri(uriOf(displayId))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(DisplayDetailResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  @DisplayName("Devolve atributos, versão do SDK e respostas na ordem da versão exibida")
  void caminho_feliz() {
    var displayId = aCompletedDisplay();
    var recent = Instant.now().minusSeconds(60);

    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(2))
        .withNumber(9)
        .answeredAt(recent)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(0))
        .withText("o relatório podia exportar em CSV")
        .answeredAt(recent)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(1))
        .withOptions("reports", "alerts")
        .answeredAt(recent)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(3))
        .skipped()
        .answeredAt(recent)
        .buildSavedIn(answers);

    var body = get(displayId);

    assertThat(body).isNotNull();
    assertThat(body.id()).isEqualTo(displayId.value());
    assertThat(body.respondentId()).isEqualTo(respondentId.value());
    assertThat(body.surveyId()).isEqualTo(surveyId.value());
    assertThat(body.versionId()).isEqualTo(versionId.value());
    assertThat(body.versionNumber()).isEqualTo(1);
    assertThat(body.outcome()).isEqualTo(DisplayOutcome.COMPLETED);
    assertThat(body.sdkVersion()).isEqualTo("1.4.2");
    assertThat(body.attributes()).containsOnly(Map.entry("plan", "pro"), Map.entry("locale", "pt-BR"));

    assertThat(body.answers())
        .extracting(AnswerReadResponseDTO::questionKey)
        .containsExactly(
            keys.get(0).value(), keys.get(1).value(), keys.get(2).value(), keys.get(3).value());
    assertThat(body.answers().get(0).text()).isEqualTo("o relatório podia exportar em CSV");
    assertThat(body.answers().get(1).options()).containsExactly("reports", "alerts");
    assertThat(body.answers().get(2).number()).isEqualTo(9);
    assertThat(body.answers().get(3).status()).isEqualTo(AnswerReadStatus.SKIPPED);

    assertThat(body.openedAt()).isEqualTo(storedInstant(displayId, "opened_at"));
    assertThat(body.closedAt()).isEqualTo(storedInstant(displayId, "closed_at"));
    assertThat(storedText(displayId, keys.get(0)))
        .isEqualTo("o relatório podia exportar em CSV");
  }

  private Instant storedInstant(DisplayId id, String column) {
    return jdbc
        .sql("select " + column + " from survey_displays where id = :id")
        .param("id", id.value())
        .query(Instant.class)
        .optional()
        .orElse(null);
  }

  private String storedText(DisplayId id, QuestionKey key) {
    return jdbc
        .sql(
            "select text_value from survey_answers where display_id = :id"
                + " and question_key = :key")
        .param("id", id.value())
        .param("key", key.value())
        .query(String.class)
        .optional()
        .orElse(null);
  }

  @Test
  @DisplayName("Texto livre vencido volta EXPIRED e sem texto, ao lado de escolha e numérica intactas")
  void texto_expirado() {
    var displayId = aCompletedDisplay();

    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(0))
        .withText("isso não deveria aparecer")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(1))
        .withOptions("reports")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(2))
        .withNumber(8)
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(3))
        .skipped()
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);

    var body = get(displayId);

    assertThat(body.answers())
        .extracting(AnswerReadResponseDTO::status)
        .containsExactly(
            AnswerReadStatus.EXPIRED,
            AnswerReadStatus.ANSWERED,
            AnswerReadStatus.ANSWERED,
            AnswerReadStatus.SKIPPED);

    var expired = body.answers().get(0);
    assertThat(expired.text()).isNull();
    assertThat(expired.number()).isNull();
    assertThat(expired.options()).isEmpty();
    assertThat(body.answers().get(1).options()).containsExactly("reports");
    assertThat(body.answers().get(2).number()).isEqualTo(8);

    // Suprimido na leitura, não apagado do banco (D-07).
    assertThat(storedText(displayId, keys.get(0))).isEqualTo("isso não deveria aparecer");
  }

  @Test
  @DisplayName("Sem prazo de retenção configurado, texto antigo continua visível")
  void sem_prazo_nao_expira() {
    var semPrazo = ApplicationFactory.anApplication().withSlug("sem-prazo").build();
    applications.save(ApplicationJpaMapper.toJpa(semPrazo));

    var displayId = displayIn(semPrazo);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(0))
        .withText("resposta antiga")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);

    var body =
        client
            .get()
            .uri("/applications/" + semPrazo.id().value() + "/displays/" + displayId.value())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(DisplayDetailResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(body.answers()).singleElement().satisfies(
        answer -> {
          assertThat(answer.status()).isEqualTo(AnswerReadStatus.ANSWERED);
          assertThat(answer.text()).isEqualTo("resposta antiga");
        });
  }

  private DisplayId displayIn(Application application) {
    var respondent =
        RespondentFactory.aRespondent()
            .forApplication(application.id())
            .identifiedByDevice("device-" + UUID.randomUUID())
            .buildSavedIn(respondents);

    return SurveyDisplayFactory.aDisplay()
        .forApplication(application.id())
        .forSurvey(surveyId)
        .forVersion(versionId)
        .forRespondent(respondent.id())
        .openedAt(OPENED_AT)
        .completedAt(OPENED_AT.plusSeconds(30))
        .buildSavedIn(displays)
        .id();
  }

  @Test
  @DisplayName("Exibição aberta devolve nenhuma resposta e nenhum fechamento; sdkVersion pode faltar")
  void exibicao_aberta() {
    var displayId =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .forRespondent(respondentId)
            .withoutSdkVersion()
            .openedAt(OPENED_AT)
            .buildSavedIn(displays)
            .id();

    var body = get(displayId);

    assertThat(body.outcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(body.closedAt()).isNull();
    assertThat(body.sdkVersion()).isNull();
    assertThat(body.answers()).isEmpty();
    assertThat(body.attributes()).isEmpty();
  }

  @Test
  @DisplayName("Exibição dispensada devolve o desfecho e nenhuma resposta")
  void exibicao_dispensada() {
    var displayId =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .forRespondent(respondentId)
            .openedAt(OPENED_AT)
            .dismissedAt(OPENED_AT.plusSeconds(5))
            .buildSavedIn(displays)
            .id();

    var body = get(displayId);

    assertThat(body.outcome()).isEqualTo(DisplayOutcome.DISMISSED);
    assertThat(body.closedAt()).isEqualTo(OPENED_AT.plusSeconds(5));
    assertThat(body.answers()).isEmpty();
  }

  @Test
  @DisplayName("Chave de aplicação na superfície administrativa responde 403")
  void recusa_chave_de_aplicacao() {
    var displayId = aCompletedDisplay();

    client
        .get()
        .uri(uriOf(displayId))
        .header(HEADER, "pit_qualquer")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");
  }

  @Test
  @DisplayName("Exibição inexistente ou de outra aplicação responde 404, nunca 403")
  void recusa_exibicao_fora_do_escopo() {
    var outra = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));
    var alheia = displayIn(outra);

    expectNotFound("/applications/" + applicationId.value() + "/displays/" + UUID.randomUUID());
    expectNotFound(uriOf(alheia));
    expectNotFound("/applications/" + applicationId.value() + "/displays/nao-e-um-id");
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
        .isEqualTo("display.not_found");
  }

  @Test
  @DisplayName("Nenhum caminho, feliz ou de recusa, altera o estado gravado")
  void nao_altera_o_estado() {
    var displayId = aCompletedDisplay();
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(keys.get(0))
        .withText("texto antigo")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);

    get(displayId);
    client.get().uri(uriOf(displayId)).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri("/applications/" + applicationId.value() + "/displays/" + UUID.randomUUID())
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(countOf("survey_displays")).isEqualTo(1);
    assertThat(countOf("survey_answers")).isEqualTo(1);
    assertThat(countOf("respondents")).isEqualTo(1);
    assertThat(storedText(displayId, keys.get(0))).isEqualTo("texto antigo");
    assertThat(storedInstant(displayId, "closed_at")).isEqualTo(OPENED_AT.plusSeconds(60));
  }
}
