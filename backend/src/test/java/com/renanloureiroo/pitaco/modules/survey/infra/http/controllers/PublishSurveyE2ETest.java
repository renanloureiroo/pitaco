package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyVersionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DefineTriggerRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationImpedimentsResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublishSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionOptionDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Publicação e leitura de versão")
class PublishSurveyE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyJpaRepository surveys;
  @Autowired SurveyVersionJpaRepository versions;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;
  private SurveyResponseDTO survey;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
    survey = createSurvey();
  }

  private SurveyResponseDTO createSurvey() {
    return client
        .post()
        .uri("/applications/" + application.getId() + "/surveys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO("NPS pós-checkout"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private String surveyUri() {
    return "/applications/" + application.getId() + "/surveys/" + survey.id();
  }

  private QuestionResponseDTO addQuestion(AddQuestionRequestDTO request) {
    return client
        .post()
        .uri(surveyUri() + "/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(QuestionResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private void defineTrigger(Instant start, Instant end) {
    client
        .put()
        .uri(surveyUri() + "/trigger")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DefineTriggerRequestDTO("checkout.completed", start, end, 0.25))
        .exchange()
        .expectStatus()
        .isOk();
  }

  private SurveyVersionResponseDTO publish() {
    return client
        .post()
        .uri(surveyUri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(PublishSurveyRequestDTO.empty())
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyVersionResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private PublicationImpedimentsResponseDTO impediments() {
    return client
        .get()
        .uri(surveyUri() + "/publication-impediments")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PublicationImpedimentsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private static AddQuestionRequestDTO freeText(String statement) {
    return new AddQuestionRequestDTO(statement, "free_text", true, null, null);
  }

  @Test
  @DisplayName("Publica com janela aberta, deixando a pesquisa ativa, e devolve 201 com Location")
  void publica_com_janela_aberta() {
    addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now().minus(1, ChronoUnit.HOURS), null);

    var result =
        client
            .post()
            .uri(surveyUri() + "/publication")
            .contentType(MediaType.APPLICATION_JSON)
            .body(PublishSurveyRequestDTO.empty())
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .exists("Location")
            .expectBody(SurveyVersionResponseDTO.class)
            .returnResult();

    var version = result.getResponseBody();
    assertThat(version.number()).isEqualTo(1);
    assertThat(version.status()).isEqualTo("published");
    assertThat(version.publishedAt()).isNotNull();
    assertThat(version.comparabilityGroup()).isEqualTo(1);
    assertThat(version.changeKind()).isNull();
    assertThat(result.getResponseHeaders().getLocation().toString()).endsWith("/versions/1");

    var stored = surveys.findById(survey.id()).orElseThrow();
    assertThat(stored.getLifecycle()).isEqualTo("PUBLISHED");
    assertThat(stored.getPublishedVersionNumber()).isEqualTo(1);
    assertThat(stored.getDraftVersionNumber()).isNull();
    assertThat(versions.findByNumber(survey.id(), 1).orElseThrow().getStatus())
        .isEqualTo("PUBLISHED");

    assertThat(getSurvey().state()).isEqualTo("active");
  }

  @Test
  @DisplayName("Publica com janela futura, deixando a pesquisa agendada")
  void publica_com_janela_futura() {
    addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now().plus(1, ChronoUnit.HOURS), null);

    publish();

    assertThat(getSurvey().state()).isEqualTo("scheduled");
  }

  @Test
  @DisplayName("Recusa a publicação com todos os impedimentos de uma vez, na extensão do corpo")
  void recusa_com_varios_impedimentos() {
    addQuestion(new AddQuestionRequestDTO("Recomendaria?", "single_choice", true, List.of(), null));

    client
        .post()
        .uri(surveyUri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(PublishSurveyRequestDTO.empty())
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_publishable")
        .jsonPath("$.impediments.length()")
        .isEqualTo(2)
        .jsonPath("$.impediments[?(@.code=='question.options_missing')].field")
        .isEqualTo(List.of("questions[0].options"))
        .jsonPath("$.impediments[?(@.code=='trigger.missing')].field")
        .isEqualTo(List.of("trigger"));

    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("Rascunho sem nenhuma pergunta acusa survey.no_questions")
  void recusa_sem_pergunta() {
    defineTrigger(Instant.now(), null);

    client
        .post()
        .uri(surveyUri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(PublishSurveyRequestDTO.empty())
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.impediments[0].code")
        .isEqualTo("survey.no_questions");
  }

  @Test
  @DisplayName("A lista consultada sem publicar é a mesma que a publicação usaria")
  void a_consulta_traz_a_mesma_lista() {
    addQuestion(new AddQuestionRequestDTO("Recomendaria?", "single_choice", true, List.of(), null));

    var consultados = impediments();

    assertThat(consultados.impediments())
        .extracting(item -> item.code())
        .containsExactlyInAnyOrder("question.options_missing", "trigger.missing");
    assertThat(consultados.impediments())
        .filteredOn(item -> item.code().equals("question.options_missing"))
        .allSatisfy(item -> assertThat(item.questionKey()).isNotNull());

    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("DRAFT");
    assertThat(versions.findDraft(survey.id())).isPresent();
  }

  @Test
  @DisplayName("Rascunho completo devolve lista de impedimentos vazia")
  void rascunho_completo_nao_tem_impedimento() {
    addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now(), null);

    assertThat(impediments().impediments()).isEmpty();
  }

  @Test
  @DisplayName("Publicar de novo, sem rascunho aberto, devolve 409")
  void recusa_publicar_de_novo() {
    addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now(), null);
    publish();

    client
        .post()
        .uri(surveyUri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(PublishSurveyRequestDTO.empty())
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.already_published");
  }

  @Test
  @DisplayName("Descartar pesquisa publicada devolve 422 e ela continua no banco")
  void recusa_descartar_publicada() {
    addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now(), null);
    publish();

    client
        .delete()
        .uri(surveyUri())
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.published_cannot_be_discarded");

    assertThat(surveys.findById(survey.id())).isPresent();
  }

  @Test
  @DisplayName("As seis escritas de conteúdo são recusadas com 422 depois da publicação")
  void conteudo_congelado_apos_publicar() {
    var question = addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now(), null);
    publish();

    var escritas =
        List.<Runnable>of(
            () ->
                expect422(
                    client
                        .post()
                        .uri(surveyUri() + "/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(freeText("Nova"))),
            () ->
                expect422(
                    client
                        .put()
                        .uri(surveyUri() + "/questions/" + question.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            new com.renanloureiroo.pitaco.modules.survey.infra.http.dtos
                                .UpdateQuestionRequestDTO("Outro", "free_text", true, null, null))),
            () -> expect422(client.delete().uri(surveyUri() + "/questions/" + question.id())),
            () ->
                expect422(
                    client
                        .put()
                        .uri(surveyUri() + "/questions/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            new com.renanloureiroo.pitaco.modules.survey.infra.http.dtos
                                .ReorderQuestionsRequestDTO(List.of(question.id())))),
            () ->
                expect422(
                    client
                        .put()
                        .uri(surveyUri() + "/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new DefineTriggerRequestDTO("app.opened", Instant.now(), null, 1.0))),
            () ->
                expect422(
                    client
                        .post()
                        .uri(surveyUri() + "/trigger/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            new com.renanloureiroo.pitaco.modules.survey.infra.http.dtos
                                .AddSegmentationRuleRequestDTO("plan", "present", null))));

    escritas.forEach(Runnable::run);
  }

  private static void expect422(RestTestClient.RequestHeadersSpec<?> spec) {
    spec.exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.content_frozen");
  }

  @Test
  @DisplayName("Publica com 20 perguntas e lê as 20 de volta na ordem, idênticas ao montado")
  void le_a_versao_publicada_inteira() {
    for (var index = 1; index <= 20; index++) {
      addQuestion(freeText("Pergunta " + index));
    }
    addQuestion(
        new AddQuestionRequestDTO(
            "Recomendaria?",
            "single_choice",
            false,
            List.of(new QuestionOptionDTO("Sim", "yes"), new QuestionOptionDTO("Não", "no")),
            null));
    defineTrigger(Instant.now(), null);
    publish();

    var version =
        client
            .get()
            .uri(surveyUri() + "/versions/1")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyVersionDetailResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(version.number()).isEqualTo(1);
    assertThat(version.status()).isEqualTo("published");
    assertThat(version.questions()).hasSize(21);
    assertThat(version.questions())
        .extracting(QuestionResponseDTO::position)
        .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 21).boxed().toList());
    assertThat(version.questions().get(0).statement()).isEqualTo("Pergunta 1");
    assertThat(version.questions().get(19).statement()).isEqualTo("Pergunta 20");
    assertThat(version.questions().get(20).options())
        .extracting(QuestionOptionDTO::value)
        .containsExactly("yes", "no");
    assertThat(version.questions()).extracting(QuestionResponseDTO::key).doesNotHaveDuplicates();
    assertThat(version.trigger().eventName()).isEqualTo("checkout.completed");
  }

  @Test
  @DisplayName("Versão inexistente devolve 404")
  void recusa_versao_inexistente() {
    addQuestion(freeText("O que achou?"));
    defineTrigger(Instant.now(), null);
    publish();

    client
        .get()
        .uri(surveyUri() + "/versions/99")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey_version.not_found");
  }

  private SurveyDetailResponseDTO getSurvey() {
    return client
        .get()
        .uri(surveyUri())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyDetailResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }
}
