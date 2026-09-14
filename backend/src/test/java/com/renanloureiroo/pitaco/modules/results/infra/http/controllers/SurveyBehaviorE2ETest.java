package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsReceiptDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.MetricDefinitionDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.QuestionBehaviorDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.ViaCountDTO;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

// Os eventos entram pela rota pública, como o SDK os mandaria, e a leitura sai pela do painel:
// o que se prova é a conta de ponta a ponta, não o SQL isolado.
@E2E
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/results/behavior")
class SurveyBehaviorE2ETest extends ResultsE2ESupport {

  @Autowired RestTestClient client;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private String key;
  private Instant firstOpening;
  private Instant laterOpening;

  @BeforeEach
  void setUp() {
    database.clean();
    seedSurvey(ApplicationFactory.anApplication());
    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app de comportamento"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();
    firstOpening = Instant.now().minus(Duration.ofHours(3));
    laterOpening = Instant.now().minus(Duration.ofHours(2));
  }

  private final class Script {

    private final List<Map<String, Object>> events = new ArrayList<>();

    Script add(String type, QuestionKey question, Map<String, Object> data) {
      var event = new LinkedHashMap<String, Object>();
      event.put("catalogVersion", 1);
      event.put("type", type);
      event.put("seq", events.size() + 1);
      event.put("occurredAt", Instant.now().toString());
      event.put("elapsedMs", events.size() * 1000L);
      if (question != null) {
        event.put("questionKey", question.value());
      }
      event.put("data", data);
      events.add(event);
      return this;
    }

    Script presented() {
      return add("survey_presented", null, Map.of("presentation", "bottom-sheet", "questionCount", 4, "renderableCount", 4));
    }

    Script viewed(QuestionKey question, int position, int visit, String from) {
      return add("question_viewed", question, Map.of("position", position, "visit", visit, "from", from));
    }

    Script left(QuestionKey question, int visit, String to, long activeMs, boolean answered) {
      return add(
          "question_left",
          question,
          Map.of("visit", visit, "to", to, "durationMs", activeMs + 100, "activeMs", activeMs, "answered", answered));
    }

    void sendTo(DisplayId display) {
      var receipt =
          client
              .post()
              .uri("/collect/displays/" + display.value() + "/events")
              .header(HEADER, key)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("events", events))
              .exchange()
              .expectStatus()
              .isAccepted()
              .expectBody(InteractionEventsReceiptDTO.class)
              .returnResult()
              .getResponseBody();
      assertThat(receipt.accepted()).isEqualTo(events.size());
    }
  }

  // D1 concluída (plano pro): volta do NPS para trocar a nota, bloqueio na escolha, avaliação
  // pulada e texto respondido. D2 dispensada na escolha por swipe (plano free). D3 abandonada no
  // NPS. D4 dispensada no NPS pelo botão de fechar. D5 concluída sem evento nenhum.
  private void seedBehavior() {
    var d1 = display(DisplayOutcome.COMPLETED, firstOpening, Map.of("plano", "pro"));
    new Script()
        .presented()
        .viewed(nps, 1, 1, "start")
        .add("answer_selected", nps, Map.of("value", 9))
        .left(nps, 1, "next", 4000, true)
        .add("navigated_next", nps, Map.of("toKey", choice.value()))
        .viewed(choice, 2, 1, "next")
        .add("validation_blocked", choice, Map.of("reason", "required_missing"))
        .add("answer_selected", choice, Map.of("value", "yes"))
        .left(choice, 1, "back", 1000, true)
        .add("navigated_back", choice, Map.of("toKey", nps.value()))
        .viewed(nps, 1, 2, "back")
        .add("answer_changed", nps, Map.of("from", 9, "to", 10))
        .left(nps, 2, "next", 2000, true)
        .viewed(choice, 2, 2, "next")
        .left(choice, 2, "next", 500, true)
        .viewed(rating, 3, 1, "next")
        .add("question_skipped", rating, Map.of())
        .left(rating, 1, "next", 700, false)
        .viewed(text, 4, 1, "next")
        .add("text_focused", text, Map.of())
        .add("text_edited", text, Map.of("length", 10, "text", "meu telefone"))
        .add("text_blurred", text, Map.of("length", 10))
        .left(text, 1, "complete", 6000, true)
        .add("survey_completed", null, Map.of("answeredCount", 3, "skippedCount", 1, "notApplicableCount", 0, "activeMs", 13700))
        .sendTo(d1);

    var d2 = display(DisplayOutcome.DISMISSED, laterOpening, Map.of("plano", "free"));
    new Script()
        .presented()
        .viewed(nps, 1, 1, "start")
        .add("answer_selected", nps, Map.of("value", 3))
        .left(nps, 1, "next", 1000, true)
        .viewed(choice, 2, 1, "next")
        .left(choice, 1, "dismiss", 2000, false)
        .add("survey_dismissed", null, Map.of("via", "swipe", "position", 2, "answeredCount", 1))
        .sendTo(d2);

    var d3 = display(DisplayOutcome.STARTED, laterOpening, Map.of());
    new Script().presented().viewed(nps, 1, 1, "start").sendTo(d3);

    var d4 = display(DisplayOutcome.DISMISSED, laterOpening, Map.of());
    new Script()
        .presented()
        .viewed(nps, 1, 1, "start")
        .left(nps, 1, "dismiss", 300, false)
        .add("survey_dismissed", null, Map.of("via", "close_button", "position", 1, "answeredCount", 0))
        .sendTo(d4);

    display(DisplayOutcome.COMPLETED, laterOpening, Map.of());
  }

  private SurveyBehaviorResponseDTO behavior(String query) {
    return client
        .get()
        .uri(base + "/behavior" + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyBehaviorResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private static QuestionBehaviorDTO question(SurveyBehaviorResponseDTO body, QuestionKey key) {
    return body.questions().stream().filter(question -> question.key().equals(key.value())).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("Funil, tempo ativo, volta, troca, bloqueio e dispensa conferem com o que foi enviado")
  void numeros_conferem() {
    seedBehavior();

    var body = behavior("");

    assertThat(body.everPublished()).isTrue();
    assertThat(body.displayed()).isEqualTo(5);
    assertThat(body.instrumented()).isEqualTo(4);
    assertThat(body.questions()).extracting(QuestionBehaviorDTO::position).containsExactly(1, 2, 3, 4);

    var npsResult = question(body, nps);
    assertThat(npsResult.type()).isEqualTo("nps");
    assertThat(npsResult.viewed()).isEqualTo(4);
    assertThat(npsResult.answered()).isEqualTo(2);
    assertThat(npsResult.skipped()).isZero();
    assertThat(npsResult.abandoned()).isEqualTo(2);
    assertThat(npsResult.revisited()).isOne();
    assertThat(npsResult.revisitRate()).isCloseTo(0.25, within(0.0001));
    assertThat(npsResult.selected()).isEqualTo(2);
    assertThat(npsResult.changed()).isOne();
    assertThat(npsResult.answerChangeRate()).isCloseTo(0.5, within(0.0001));
    assertThat(npsResult.activeTime().samples()).isEqualTo(3);
    assertThat(npsResult.activeTime().medianMs()).isEqualTo(1000L);
    assertThat(npsResult.activeTime().p90Ms()).isEqualTo(5000L);
    assertThat(npsResult.validationBlocks()).isZero();

    var choiceResult = question(body, choice);
    assertThat(choiceResult.viewed()).isEqualTo(2);
    assertThat(choiceResult.answered()).isOne();
    assertThat(choiceResult.abandoned()).isOne();
    assertThat(choiceResult.revisitRate()).isCloseTo(0.5, within(0.0001));
    assertThat(choiceResult.answerChangeRate()).isCloseTo(0.0, within(0.0001));
    assertThat(choiceResult.validationBlockedDisplays()).isOne();
    assertThat(choiceResult.validationBlocks()).isOne();
    assertThat(choiceResult.activeTime().medianMs()).isEqualTo(1750L);
    assertThat(choiceResult.activeTime().p90Ms()).isEqualTo(1950L);

    var ratingResult = question(body, rating);
    assertThat(ratingResult.viewed()).isOne();
    assertThat(ratingResult.answered()).isZero();
    assertThat(ratingResult.skipped()).isOne();
    assertThat(ratingResult.answerChangeRate()).isNull();
    assertThat(ratingResult.activeTime().medianMs()).isEqualTo(700L);

    var textResult = question(body, text);
    assertThat(textResult.answered()).isOne();
    assertThat(textResult.activeTime().p90Ms()).isEqualTo(6000L);

    assertThat(body.dismissals().total()).isEqualTo(2);
    assertThat(body.dismissals().unspecified()).isZero();
    assertThat(body.dismissals().byVia())
        .extracting(ViaCountDTO::via, ViaCountDTO::count)
        .containsExactly(
            Tuple.tuple("close_button", 1L),
            Tuple.tuple("swipe", 1L),
            Tuple.tuple("backdrop", 0L),
            Tuple.tuple("hardware_back", 0L),
            Tuple.tuple("navigation", 0L),
            Tuple.tuple("programmatic", 0L));
    assertThat(body.dismissals().byVia().get(1).share()).isCloseTo(0.5, within(0.0001));
    assertThat(body.definitions()).extracting(MetricDefinitionDTO::metric).contains("revisitRate", "activeTime", "abandoned");
  }

  @Test
  @DisplayName("Os recortes de atributo, período e versão são os mesmos dos resultados")
  void recortes() {
    seedBehavior();

    var pro = behavior("?attribute=plano&attributeValue=pro");
    assertThat(pro.displayed()).isOne();
    assertThat(pro.instrumented()).isOne();
    assertThat(question(pro, nps).viewed()).isOne();
    assertThat(question(pro, nps).abandoned()).isZero();
    assertThat(pro.dismissals().total()).isZero();
    assertThat(pro.filter().attributeValue()).isEqualTo("pro");

    var later = behavior("?from=" + laterOpening.minus(Duration.ofMinutes(30)));
    assertThat(later.displayed()).isEqualTo(4);
    assertThat(later.instrumented()).isEqualTo(3);
    assertThat(question(later, nps).viewed()).isEqualTo(3);
    assertThat(question(later, nps).activeTime().medianMs()).isEqualTo(650L);

    var otherVersion = behavior("?version=2");
    assertThat(otherVersion.displayed()).isZero();
    assertThat(otherVersion.questions()).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa em rascunho responde vazio; recorte inválido é 400")
  void rascunho_e_recorte_invalido() {
    var draft = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);

    var empty =
        client
            .get()
            .uri("/applications/" + applicationId.value() + "/surveys/" + draft.id().value() + "/results/behavior")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyBehaviorResponseDTO.class)
            .returnResult()
            .getResponseBody();
    assertThat(empty.everPublished()).isFalse();
    assertThat(empty.questions()).isEmpty();

    client
        .get()
        .uri(base + "/behavior?version=0")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }

  @Test
  @DisplayName("Chave de aplicação responde 403; pesquisa fora do escopo responde 404")
  void recusa_chave_e_escopo() {
    client
        .get()
        .uri(base + "/behavior")
        .header(HEADER, key)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");

    var other = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper.toJpa(other));
    var alheia =
        SurveyFactory.aSurvey().forApplication(other.id()).published(1).inLifecycle(SurveyLifecycle.PUBLISHED).buildSavedIn(surveys);

    for (var target :
        List.of(
            "/applications/" + applicationId.value() + "/surveys/" + UUID.randomUUID() + "/results/behavior",
            "/applications/" + applicationId.value() + "/surveys/" + alheia.id().value() + "/results/behavior",
            "/applications/" + applicationId.value() + "/surveys/nao-e-id/results/behavior")) {
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
  }

  @Test
  @DisplayName("A leitura não altera o que foi gravado")
  void nao_altera_o_estado() {
    seedBehavior();
    var before = jdbc.sql("select count(*) from survey_display_events").query(Long.class).single();

    behavior("");
    behavior("?attribute=plano");

    assertThat(jdbc.sql("select count(*) from survey_display_events").query(Long.class).single()).isEqualTo(before);
  }
}
