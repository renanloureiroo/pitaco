package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.AnswerDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyDisplayResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
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
@DisplayName("POST /collect/displays")
class CollectDisplayE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String DISPLAYS = "/collect/displays";
  private static final String REFERENCE = "u-8f1c";

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private String key;
  private SurveyVersion published;
  private String surveyId;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    surveyId = survey.id().value();

    published =
        SurveyVersionFactory.aVersion()
            .forSurvey(survey.id())
            .withQuestions(
                QuestionFactory.aFreeTextQuestion().withStatement("O que achou?"),
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?").optional())
            .triggeredBy(TriggerFactory.aTrigger().forEvent("checkout.completed").withRate(1.0))
            .buildPublishedSavedIn(versions);
  }

  @Test
  @DisplayName("Abre a exibição com 201 e Location, e a linha é lida de volta do banco")
  void abre_a_exibicao() {
    var displayId = UUID.randomUUID().toString();

    var response =
        client
            .post()
            .uri(DISPLAYS)
            .header(HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(openRequest(displayId))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(SurveyDisplayResponseDTO.class)
            .returnResult();

    var body = response.getResponseBody();
    assertThat(body).isNotNull();
    assertThat(body.displayId()).isEqualTo(displayId);
    assertThat(body.outcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(response.getResponseHeaders().getLocation())
        .asString()
        .endsWith("/api/collect/displays/" + displayId);

    assertThat(outcomeOf(displayId)).isEqualTo("STARTED");
    assertThat(countOf("respondents")).isEqualTo(1);
    assertThat(
            jdbc.sql("select value from survey_display_attributes where display_id = :id and name = 'plano'")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo("premium");
  }

  @Test
  @DisplayName("A mesma abertura de novo devolve 200 e não cria segunda exibição")
  void reabertura_devolve_200() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(openRequest(displayId))
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(countOf("survey_displays")).isEqualTo(1);
    assertThat(countOf("respondents")).isEqualTo(1);
  }

  @Test
  @DisplayName("Abertura com versão em rascunho devolve 404 survey_version.not_found")
  void abertura_com_rascunho_e_404() {
    var draftSurvey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    var draft =
        SurveyVersionFactory.aVersion()
            .forSurvey(draftSurvey.id())
            .withQuestions(QuestionFactory.aFreeTextQuestion())
            .triggeredBy(TriggerFactory.aTrigger().forEvent("checkout.completed"))
            .buildSavedIn(versions);

    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                UUID.randomUUID().toString(),
                draftSurvey.id().value(),
                draft.id().value(),
                new RespondentDTO(REFERENCE, null),
                null,
                null))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey_version.not_found");

    assertThat(countOf("survey_displays")).isZero();
  }

  @Test
  @DisplayName("O mesmo identificador para outra pesquisa devolve 409")
  void identificador_conflitante_e_409() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    var outraPesquisa =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    var outraVersao =
        SurveyVersionFactory.aVersion()
            .forSurvey(outraPesquisa.id())
            .withQuestions(QuestionFactory.aFreeTextQuestion())
            .triggeredBy(TriggerFactory.aTrigger().forEvent("outro.evento"))
            .buildPublishedSavedIn(versions);

    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                displayId,
                outraPesquisa.id().value(),
                outraVersao.id().value(),
                new RespondentDTO(REFERENCE, null),
                null,
                null))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("display.identifier_conflict");
  }

  @Test
  @DisplayName("Identificador de exibição fora do formato UUID devolve 400")
  void identificador_invalido_e_400() {
    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                "nao-e-uuid",
                surveyId,
                published.id().value(),
                new RespondentDTO(REFERENCE, null),
                null,
                null))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }

  @Test
  @DisplayName("Submete com 204 e as respostas voltam do banco amarradas à versão exibida")
  void submete_e_le_de_volta() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    submit(displayId, completion()).expectStatus().isNoContent();

    assertThat(outcomeOf(displayId)).isEqualTo("COMPLETED");
    assertThat(
            jdbc.sql("select closed_at from survey_displays where id = :id")
                .param("id", displayId)
                .query(java.time.Instant.class)
                .single())
        .isNotNull();
    assertThat(
            jdbc.sql("select version_id from survey_displays where id = :id")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo(published.id().value());

    assertThat(countOf("survey_answers")).isEqualTo(2);
    assertThat(
            jdbc.sql(
                    "select text_value from survey_answers where display_id = :id and question_key = :key")
                .param("id", displayId)
                .param("key", questionKeyOf(0))
                .query(String.class)
                .single())
        .isEqualTo("achei o frete caro");
    assertThat(
            jdbc.sql(
                    "select numeric_value from survey_answers where display_id = :id and question_key = :key")
                .param("id", displayId)
                .param("key", questionKeyOf(1))
                .query(Integer.class)
                .single())
        .isEqualTo(9);
  }

  @Test
  @DisplayName("O mesmo pacote de novo devolve 204 e não cria segunda resposta")
  void reenvio_identico_nao_duplica() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    submit(displayId, completion()).expectStatus().isNoContent();
    submit(displayId, completion()).expectStatus().isNoContent();

    assertThat(countOf("survey_answers")).isEqualTo(2);
  }

  @Test
  @DisplayName("Envio inválido devolve 422 com todos os problemas de uma vez e grava zero linhas")
  void envio_invalido_lista_tudo_e_nao_grava() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    var intrusa = UUID.randomUUID().toString();
    var invalido =
        new SubmissionRequestDTO(
            SubmissionRequestDTO.Outcome.COMPLETED,
            List.of(
                new AnswerDTO(intrusa, AnswerStatus.ANSWERED, "x"),
                new AnswerDTO(questionKeyOf(1), AnswerStatus.ANSWERED, 11)));

    submit(displayId, invalido)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("submission.rejected")
        .jsonPath("$.errors.length()")
        .isEqualTo(3)
        .jsonPath("$.errors[0].code")
        .isEqualTo("answer.question_unknown")
        .jsonPath("$.errors[1].code")
        .isEqualTo("answer.required_missing")
        .jsonPath("$.errors[2].code")
        .isEqualTo("answer.value_out_of_range")
        .jsonPath("$.traceId")
        .exists();

    assertThat(countOf("survey_answers")).isZero();
    assertThat(outcomeOf(displayId)).isEqualTo("STARTED");
  }

  @Test
  @DisplayName("Exibição de outra aplicação devolve 404 display.not_found")
  void exibicao_de_outra_aplicacao_e_404() {
    var outra = ApplicationFactory.anApplication().withSlug("outra-app").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));
    var outraChave = ApiKey.issue(outra.id(), ApiKeyLabel.of("outra"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(outraChave.apiKey()));

    var displayId = UUID.randomUUID().toString();
    open(displayId);

    client
        .post()
        .uri(DISPLAYS + "/" + displayId + "/submission")
        .header(HEADER, outraChave.plainSecret())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.DISMISSED, List.of()))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("display.not_found");
  }

  @Test
  @DisplayName("Abertura apontando a versão de outra aplicação devolve o mesmo 404 (SC-012)")
  void abertura_com_versao_alheia_e_404() {
    var outra = ApplicationFactory.anApplication().withSlug("alheia").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));

    var alheia =
        SurveyFactory.aSurvey()
            .forApplication(outra.id())
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    var versaoAlheia =
        SurveyVersionFactory.aVersion()
            .forSurvey(alheia.id())
            .withQuestions(QuestionFactory.aFreeTextQuestion())
            .triggeredBy(TriggerFactory.aTrigger().forEvent("checkout.completed"))
            .buildPublishedSavedIn(versions);

    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                UUID.randomUUID().toString(),
                alheia.id().value(),
                versaoAlheia.id().value(),
                new RespondentDTO(REFERENCE, null),
                null,
                null))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey_version.not_found");

    assertThat(countOf("survey_displays")).isZero();
  }

  @Test
  @DisplayName("A resposta atrasada continua valendo depois de a pesquisa sair do ar (SC-014)")
  void resposta_atrasada_continua_valendo() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    // A pesquisa sai do ar entre a abertura e o envio: a elegibilidade já para de entregar, mas
    // a submissão continua valendo, porque a validação usa a versão exibida.
    var survey =
        surveys
            .findByIdAndApplicationId(published.getSurveyId(), applicationId)
            .orElseThrow();
    survey.pause();
    surveys.update(survey);

    submit(displayId, completion()).expectStatus().isNoContent();

    assertThat(outcomeOf(displayId)).isEqualTo("COMPLETED");
    assertThat(
            jdbc.sql("select version_id from survey_displays where id = :id")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo(published.id().value());
    assertThat(countOf("survey_answers")).isEqualTo(2);
  }

  @Test
  @DisplayName("Corpo malformado devolve 400")
  void json_malformado_e_400() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    client
        .post()
        .uri(DISPLAYS + "/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"outcome\": ")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }

  @Test
  @DisplayName("Desfecho desconhecido devolve 400")
  void desfecho_desconhecido_e_400() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    client
        .post()
        .uri(DISPLAYS + "/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"outcome\": \"ABANDONED\", \"answers\": []}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }

  private SubmissionRequestDTO completion() {
    return new SubmissionRequestDTO(
        SubmissionRequestDTO.Outcome.COMPLETED,
        List.of(
            new AnswerDTO(questionKeyOf(0), AnswerStatus.ANSWERED, "achei o frete caro"),
            new AnswerDTO(questionKeyOf(1), AnswerStatus.ANSWERED, 9)));
  }

  private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec submit(
      String displayId, Object body) {
    return client
        .post()
        .uri(DISPLAYS + "/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private void open(String displayId) {
    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(openRequest(displayId))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private OpenDisplayRequestDTO openRequest(String displayId) {
    return new OpenDisplayRequestDTO(
        displayId,
        surveyId,
        published.id().value(),
        new RespondentDTO(REFERENCE, null),
        Map.of("plano", "premium"),
        "1.4.2");
  }

  private String questionKeyOf(int index) {
    return published.getQuestions().stream()
        .sorted(java.util.Comparator.comparingInt(Question::getPosition))
        .toList()
        .get(index)
        .getKey()
        .value();
  }

  private String outcomeOf(String displayId) {
    return jdbc
        .sql("select outcome from survey_displays where id = :id")
        .param("id", displayId)
        .query(String.class)
        .single();
  }

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}
