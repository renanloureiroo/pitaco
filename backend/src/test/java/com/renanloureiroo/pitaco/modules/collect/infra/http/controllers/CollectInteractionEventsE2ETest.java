package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static com.renanloureiroo.pitaco.modules.collect.infra.http.controllers.HealthE2ESupport.KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.InteractionEventsReceiptDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("POST /collect/displays/{displayId}/events")
class CollectInteractionEventsE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired RespondentRepository respondents;
  @Autowired SurveyDisplayRepository displays;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private HealthE2ESupport.Tenant tenant;
  private SurveyVersion version;
  private QuestionKey choice;
  private QuestionKey text;
  private DisplayId display;

  @BeforeEach
  void setUp() {
    database.clean();
    tenant = HealthE2ESupport.tenant(applications, apiKeys, "app-eventos");

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(tenant.applicationId())
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    version =
        SurveyVersionFactory.aVersion()
            .forSurvey(survey.id())
            .withQuestions(
                QuestionFactory.aSingleChoiceQuestion().withStatement("Recomendaria?"),
                QuestionFactory.aFreeTextQuestion().withStatement("O que achou?").optional())
            .triggeredBy(TriggerFactory.aTrigger().forEvent(HealthE2ESupport.EVENT).withRate(1.0))
            .buildPublishedSavedIn(versions);
    choice = keyAt(1);
    text = keyAt(2);
    display = openedAt(Instant.now().minus(Duration.ofHours(1)));
  }

  private QuestionKey keyAt(int position) {
    return version.getQuestions().stream()
        .filter(question -> question.getPosition() == position)
        .findFirst()
        .orElseThrow()
        .getKey();
  }

  private DisplayId openedAt(Instant openedAt) {
    var respondent =
        RespondentFactory.aRespondent()
            .forApplication(tenant.applicationId())
            .identifiedByReference("u-" + UUID.randomUUID())
            .buildSavedIn(respondents)
            .id();
    return SurveyDisplayFactory.aDisplay()
        .forApplication(tenant.applicationId())
        .forSurvey(version.getSurveyId())
        .forVersion(version.id())
        .forRespondent(respondent)
        .openedAt(openedAt)
        .buildSavedIn(displays)
        .id();
  }

  private static Map<String, Object> event(
      int catalogVersion, int seq, String type, QuestionKey key, Map<String, Object> data) {
    var event = new LinkedHashMap<String, Object>();
    event.put("catalogVersion", catalogVersion);
    event.put("type", type);
    event.put("seq", seq);
    event.put("occurredAt", "2026-09-12T13:45:00.120Z");
    event.put("elapsedMs", seq * 100);
    if (key != null) {
      event.put("questionKey", key.value());
    }
    event.put("data", data);
    event.put("extra", "ignorado");
    return event;
  }

  private static Map<String, Object> event(int seq, String type, QuestionKey key, Map<String, Object> data) {
    return event(1, seq, type, key, data);
  }

  private List<Map<String, Object>> happyBatch() {
    return List.of(
        event(1, "survey_presented", null, Map.of("presentation", "bottom-sheet", "questionCount", 2, "renderableCount", 2)),
        event(2, "question_viewed", choice, Map.of("position", 1, "visit", 1, "from", "start")),
        event(3, "answer_selected", choice, Map.of("value", "yes", "label", "Sim")));
  }

  private InteractionEventsReceiptDTO post(String key, String displayId, List<Map<String, Object>> events) {
    return client
        .post()
        .uri("/collect/displays/" + displayId + "/events")
        .header(KEY_HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("events", events))
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody(InteractionEventsReceiptDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private InteractionEventsReceiptDTO post(List<Map<String, Object>> events) {
    return post(tenant.key(), display.value(), events);
  }

  private long count() {
    return jdbc.sql("select count(*) from survey_display_events").query(Long.class).single();
  }

  @Test
  @DisplayName("202 grava o lote, e as linhas lidas do banco trazem o envelope e o payload fechado")
  void grava_o_lote() {
    var receipt = post(happyBatch());

    assertThat(receipt.accepted()).isEqualTo(3);
    assertThat(receipt.duplicated()).isZero();
    assertThat(receipt.discarded())
        .isEqualTo(new InteractionEventsReceiptDTO.DiscardedEventsDTO(0, 0, 0, 0, 0, 0));

    var row =
        jdbc.sql(
                "select display_id, type, question_key, catalog_version, elapsed_ms,"
                    + " cast(data as text) as data, received_at from survey_display_events"
                    + " where seq = 3")
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("display_id", display.value())
        .containsEntry("type", "answer_selected")
        .containsEntry("question_key", choice.value())
        .containsEntry("catalog_version", 1)
        .containsEntry("elapsed_ms", 300L)
        .containsEntry("data", "{\"value\": \"yes\"}");
    assertThat(row.get("received_at")).isNotNull();
    assertThat(count()).isEqualTo(3);
  }

  @Test
  @DisplayName("Reenviar o mesmo lote não duplica; seq repetido no próprio lote grava uma vez")
  void reenvio_nao_duplica() {
    post(happyBatch());

    var replay = post(happyBatch());
    var repeated =
        post(
            List.of(
                event(4, "survey_backgrounded", null, Map.of()),
                event(4, "survey_foregrounded", null, Map.of("backgroundMs", 10))));

    assertThat(replay.accepted()).isZero();
    assertThat(replay.duplicated()).isEqualTo(3);
    assertThat(repeated.accepted()).isOne();
    assertThat(repeated.duplicated()).isOne();
    assertThat(count()).isEqualTo(4);
    assertThat(jdbc.sql("select type from survey_display_events where seq = 4").query(String.class).single())
        .isEqualTo("survey_backgrounded");
  }

  @Test
  @DisplayName("Exibição de outra aplicação responde como a inexistente, e nada é gravado")
  void isolamento_entre_aplicacoes() {
    var other = HealthE2ESupport.tenant(applications, apiKeys, "outra-app");

    var foreign = post(other.key(), display.value(), happyBatch());
    var missing = post(tenant.key(), UUID.randomUUID().toString(), happyBatch());
    var malformed = post(tenant.key(), "nao-e-uuid", happyBatch());

    assertThat(foreign).isEqualTo(missing).isEqualTo(malformed);
    assertThat(foreign.accepted()).isZero();
    assertThat(foreign.discarded().displayUnavailable()).isEqualTo(3);
    assertThat(count()).isZero();
  }

  @Test
  @DisplayName("Tipo desconhecido é descartado e contado; catalogVersion mais nova grava o que é conhecido")
  void tipo_desconhecido_e_catalogo_mais_novo() {
    var receipt =
        post(
            List.of(
                event(2, 1, "survey_teleported", null, Map.of("where", "moon")),
                event(2, 2, "survey_backgrounded", null, Map.of("reason", "phone_call"))));

    assertThat(receipt.accepted()).isOne();
    assertThat(receipt.discarded().unknownType()).isOne();
    var row =
        jdbc.sql("select type, catalog_version, cast(data as text) as data from survey_display_events")
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("type", "survey_backgrounded")
        .containsEntry("catalog_version", 2)
        .containsEntry("data", "{}");
  }

  @Test
  @DisplayName("Conteúdo de texto nunca é gravado: só length sobrevive, e escolha em texto livre é descartada")
  void texto_descartado() {
    var receipt =
        post(
            List.of(
                event(1, "text_edited", text, Map.of("length", 12, "text", "meu telefone", "value", "9999")),
                event(2, "text_blurred", text, Map.of("length", "doze")),
                event(3, "answer_selected", text, Map.of("value", "meu email"))));

    assertThat(receipt.accepted()).isEqualTo(2);
    assertThat(receipt.discarded().invalidEnvelope()).isOne();
    assertThat(
            jdbc.sql("select cast(data as text) from survey_display_events order by seq")
                .query(String.class)
                .list())
        .containsExactly("{\"length\": 12}", "{}");
    assertThat(
            jdbc.sql("select count(*) from survey_display_events where cast(data as text) like '%meu%'")
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  @DisplayName("Pergunta fora da versão exibida e envelope incompleto são descartados; o resto grava")
  void descartes_por_evento() {
    var incomplete = new LinkedHashMap<>(event(2, "survey_backgrounded", null, Map.of()));
    incomplete.remove("seq");

    var receipt =
        post(
            List.of(
                event(1, "question_viewed", QuestionKey.generate(), Map.of("position", 1, "visit", 1, "from", "start")),
                incomplete,
                event(3, "survey_foregrounded", null, Map.of("backgroundMs", 500))));

    assertThat(receipt.accepted()).isOne();
    assertThat(receipt.discarded().unknownQuestion()).isOne();
    assertThat(receipt.discarded().invalidEnvelope()).isOne();
    assertThat(count()).isOne();
  }

  @Test
  @DisplayName("O teto de 500 por exibição corta o excesso, e o que passou dele não entra depois")
  void teto_por_exibicao() {
    for (var batch = 0; batch < 5; batch++) {
      var start = batch * 100 + 1;
      var events = new ArrayList<Map<String, Object>>();
      IntStream.range(start, start + 100).forEach(seq -> events.add(event(seq, "survey_backgrounded", null, Map.of())));
      assertThat(post(events).accepted()).isEqualTo(100);
    }

    var over = post(List.of(event(501, "survey_backgrounded", null, Map.of()), event(502, "survey_foregrounded", null, Map.of())));

    assertThat(over.accepted()).isZero();
    assertThat(over.discarded().overLimit()).isEqualTo(2);
    assertThat(count()).isEqualTo(500);
  }

  @Test
  @DisplayName("Depois da janela de sete dias da abertura, o lote é descartado; antes dela, gravado")
  void janela_de_aceitacao() {
    var late = openedAt(Instant.now().minus(Duration.ofDays(8)));
    var inTime = openedAt(Instant.now().minus(Duration.ofDays(6)));

    var discarded = post(tenant.key(), late.value(), happyBatch());
    var accepted = post(tenant.key(), inTime.value(), happyBatch());

    assertThat(discarded.discarded().outsideWindow()).isEqualTo(3);
    assertThat(accepted.accepted()).isEqualTo(3);
    assertThat(jdbc.sql("select count(*) from survey_display_events where display_id = ?").param(late.value()).query(Long.class).single())
        .isZero();
  }

  @Test
  @DisplayName("Lote vazio, acima de 100 ou ausente é 400 com o campo, e nada é gravado")
  void lote_invalido() {
    var tooMany = new ArrayList<Map<String, Object>>();
    IntStream.rangeClosed(1, 101).forEach(seq -> tooMany.add(event(seq, "survey_backgrounded", null, Map.of())));

    for (var body : List.<Object>of(Map.of("events", List.of()), Map.of("events", tooMany))) {
      client
          .post()
          .uri("/collect/displays/" + display.value() + "/events")
          .header(KEY_HEADER, tenant.key())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("request.invalid")
          .jsonPath("$.errors.events")
          .isEqualTo("Lote deve ter de 1 a 100 eventos");
    }

    client
        .post()
        .uri("/collect/displays/" + display.value() + "/events")
        .header(KEY_HEADER, tenant.key())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors.events")
        .isEqualTo("Lote de eventos é obrigatório");

    assertThat(count()).isZero();
  }

  @Test
  @DisplayName("JSON malformado e seq com tipo errado são 400, sem gravar")
  void json_malformado() {
    for (var body : List.of("{\"events\": [", "{\"events\": [{\"type\": \"survey_backgrounded\", \"seq\": \"um\"}]}")) {
      client
          .post()
          .uri("/collect/displays/" + display.value() + "/events")
          .header(KEY_HEADER, tenant.key())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    assertThat(count()).isZero();
  }

  @Test
  @DisplayName("Sem chave é 401, e com chave desconhecida também; nada é gravado")
  void sem_chave() {
    client
        .post()
        .uri("/collect/displays/" + display.value() + "/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("events", happyBatch()))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.missing");

    client
        .post()
        .uri("/collect/displays/" + display.value() + "/events")
        .header(KEY_HEADER, "pit_desconhecida")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("events", happyBatch()))
        .exchange()
        .expectStatus()
        .isUnauthorized();

    assertThat(count()).isZero();
  }

  @Test
  @DisplayName("Apagar a exibição leva os eventos dela")
  void cascata_da_exibicao() {
    post(happyBatch());

    jdbc.sql("delete from survey_displays where id = ?").param(display.value()).update();

    assertThat(count()).isZero();
  }
}
