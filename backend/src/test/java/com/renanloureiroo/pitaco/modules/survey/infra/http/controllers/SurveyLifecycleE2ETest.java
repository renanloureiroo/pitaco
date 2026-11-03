package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyStateTransitionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DefineTriggerRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublishSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyStateTransitionResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Ciclo de vida e histórico de transições")
class SurveyLifecycleE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyJpaRepository surveys;
  @Autowired SurveyStateTransitionJpaRepository transitions;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
  }

  private SurveyResponseDTO createSurvey(String name) {
    return client
        .post()
        .uri("/applications/" + application.getId() + "/surveys")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateSurveyRequestDTO(name))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private String uri(String surveyId) {
    return "/applications/" + application.getId() + "/surveys/" + surveyId;
  }

  private SurveyResponseDTO publishedSurvey(String name, Instant start, Instant end) {
    var survey = createSurvey(name);

    client
        .post()
        .uri(uri(survey.id()) + "/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new AddQuestionRequestDTO("O que achou?", "free_text", true, null, null))
        .exchange()
        .expectStatus()
        .isCreated();
    client
        .put()
        .uri(uri(survey.id()) + "/trigger")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DefineTriggerRequestDTO("checkout.completed", start, end, 0.25))
        .exchange()
        .expectStatus()
        .isOk();
    client
        .post()
        .uri(uri(survey.id()) + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(PublishSurveyRequestDTO.empty())
        .exchange()
        .expectStatus()
        .isCreated();

    return survey;
  }

  private SurveyResponseDTO command(String surveyId, String command) {
    return client
        .post()
        .uri(uri(surveyId) + "/" + command)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private void expect422(String surveyId, String command, String code) {
    client
        .post()
        .uri(uri(surveyId) + "/" + command)
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo(code);
  }

  private String stateOf(String surveyId) {
    return client
        .get()
        .uri(uri(surveyId))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyDetailResponseDTO.class)
        .returnResult()
        .getResponseBody()
        .state();
  }

  private List<SurveyStateTransitionResponseDTO> history(String surveyId) {
    return client
        .get()
        .uri(uri(surveyId) + "/transitions")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<SurveyStateTransitionResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Pausa, retoma e encerra, conferindo o ciclo de vida no banco a cada passo")
  void pausa_retoma_e_encerra() {
    var survey = publishedSurvey("NPS", Instant.now().minus(1, ChronoUnit.HOURS), null);

    assertThat(command(survey.id(), "pause").state()).isEqualTo("paused");
    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("PAUSED");

    assertThat(command(survey.id(), "resume").state()).isEqualTo("active");
    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("PUBLISHED");

    assertThat(command(survey.id(), "end").state()).isEqualTo("ended");
    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("ENDED");
  }

  @Test
  @DisplayName("Retomar pausada com janela futura devolve agendada")
  void retomar_com_janela_futura_devolve_agendada() {
    var survey = publishedSurvey("Agendada", Instant.now().plus(1, ChronoUnit.HOURS), null);
    command(survey.id(), "pause");

    assertThat(command(survey.id(), "resume").state()).isEqualTo("scheduled");
  }

  @Test
  @DisplayName("Encerrada recusa toda transição, com o estado intacto")
  void encerrada_recusa_tudo() {
    var survey = publishedSurvey("Encerrada", Instant.now().minus(1, ChronoUnit.HOURS), null);
    command(survey.id(), "end");

    expect422(survey.id(), "pause", "survey.transition_not_allowed");
    expect422(survey.id(), "resume", "survey.transition_not_allowed");
    expect422(survey.id(), "end", "survey.transition_not_allowed");

    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("ENDED");
  }

  @Test
  @DisplayName("Rascunho não tem o que pausar nem encerrar")
  void rascunho_recusa_pausar_e_encerrar() {
    var survey = createSurvey("Só rascunho");

    expect422(survey.id(), "pause", "survey.not_published");
    expect422(survey.id(), "end", "survey.not_published");
    expect422(survey.id(), "resume", "survey.not_published");

    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("DRAFT");
    assertThat(transitions.count()).isZero();
  }

  @Test
  @DisplayName("Agendada aparece ativa depois que a janela abre; ativa vira encerrada ao fechar")
  void a_janela_move_o_estado_sem_ninguem_comandar() {
    var agendada = publishedSurvey("Agendada", Instant.now().plus(1, ChronoUnit.HOURS), null);
    var ativa = publishedSurvey("Ativa", Instant.now().minus(1, ChronoUnit.HOURS), null);
    var encerradaPelaJanela =
        publishedSurvey(
            "Fechada",
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS));

    assertThat(stateOf(agendada.id())).isEqualTo("scheduled");
    assertThat(stateOf(ativa.id())).isEqualTo("active");
    assertThat(stateOf(encerradaPelaJanela.id())).isEqualTo("ended");

    assertThat(surveys.findById(encerradaPelaJanela.id()).orElseThrow().getLifecycle())
        .isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("Pausada com janela fechada continua pausada")
  void pausada_com_janela_fechada_continua_pausada() {
    var survey =
        publishedSurvey(
            "Pausada",
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS));

    command(survey.id(), "pause");

    assertThat(stateOf(survey.id())).isEqualTo("paused");
  }

  @Test
  @DisplayName("O histórico traz a publicação, as manuais e as de janela, ordenadas")
  void o_historico_soma_comandadas_e_derivadas() {
    var survey =
        publishedSurvey(
            "Com histórico",
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS));
    command(survey.id(), "pause");

    var history = history(survey.id());

    assertThat(history)
        .extracting(SurveyStateTransitionResponseDTO::reason)
        .contains("publication", "manual_pause", "window_opened", "window_closed");
    assertThat(history)
        .isSortedAccordingTo(Comparator.comparing(SurveyStateTransitionResponseDTO::occurredAt));
    assertThat(history)
        .allSatisfy(
            transition -> {
              assertThat(transition.from()).isNotBlank();
              assertThat(transition.to()).isNotBlank();
              assertThat(transition.actor()).isNull();
            });
  }

  @Test
  @DisplayName("Ler o histórico não grava nenhuma linha de janela na tabela")
  void ler_o_historico_nao_grava_transicao_de_janela() {
    var survey =
        publishedSurvey(
            "Com janela",
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS));
    var gravadasAntes = transitions.count();

    history(survey.id());
    history(survey.id());

    assertThat(transitions.count()).isEqualTo(gravadasAntes);
    assertThat(transitions.findAll())
        .extracting(transition -> transition.getReason())
        .doesNotContain("WINDOW_OPENED", "WINDOW_CLOSED");
  }

  @Test
  @DisplayName("Fora do escopo da aplicação é 404 nos comandos e no histórico")
  void recusa_fora_do_escopo() {
    var survey = publishedSurvey("Minha", Instant.now().minus(1, ChronoUnit.HOURS), null);
    var outra =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("outra-app").build()));
    var alheia = "/applications/" + outra.getId() + "/surveys/" + survey.id();

    client.post().uri(alheia + "/pause").exchange().expectStatus().isNotFound();
    client.get().uri(alheia + "/transitions").exchange().expectStatus().isNotFound();

    assertThat(surveys.findById(survey.id()).orElseThrow().getLifecycle()).isEqualTo("PUBLISHED");
  }
}
