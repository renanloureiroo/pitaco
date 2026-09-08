package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SegmentationRuleJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyVersionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyVersionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddSegmentationRuleRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DefineTriggerRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SegmentationRuleResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.TriggerResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("/applications/{applicationId}/surveys/{surveyId}/trigger")
class TriggerE2ETest {

  private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyVersionJpaRepository versions;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;
  private SurveyResponseDTO survey;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
    survey =
        client
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

  private String uri() {
    return "/applications/" + application.getId() + "/surveys/" + survey.id() + "/trigger";
  }

  private TriggerResponseDTO define(DefineTriggerRequestDTO request) {
    return client
        .put()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(TriggerResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private SurveyVersionJpaEntity storedVersion() {
    return versions.findDraft(survey.id()).orElseThrow();
  }

  @Test
  @DisplayName("Define o disparo e o relê do banco, com a janela e a proporção")
  void define_o_disparo() {
    var response =
        define(
            new DefineTriggerRequestDTO(
                "checkout.completed", START, START.plus(7, ChronoUnit.DAYS), 0.25));

    assertThat(response.eventName()).isEqualTo("checkout.completed");
    assertThat(response.windowStart()).isEqualTo(START);
    assertThat(response.windowEnd()).isEqualTo(START.plus(7, ChronoUnit.DAYS));
    assertThat(response.samplingRate()).isEqualTo(0.25);
    assertThat(response.rules()).isEmpty();

    var stored = storedVersion();
    assertThat(stored.getTriggerEventName()).isEqualTo("checkout.completed");
    assertThat(stored.getTriggerWindowStart()).isEqualTo(START);
    assertThat(stored.getTriggerWindowEnd()).isEqualTo(START.plus(7, ChronoUnit.DAYS));
    assertThat(stored.getTriggerSamplingRate()).isEqualByComparingTo("0.2500");
  }

  @Test
  @DisplayName("Janela sem fim é aceita e grava nulo na coluna")
  void aceita_janela_sem_fim() {
    define(new DefineTriggerRequestDTO("checkout.completed", START, null, 1.0));

    assertThat(storedVersion().getTriggerWindowEnd()).isNull();
  }

  @Test
  @DisplayName("Redefinir substitui, continuando um só, e preserva as regras")
  void redefinir_substitui() {
    define(new DefineTriggerRequestDTO("checkout.completed", START, null, 0.25));
    addRule(new AddSegmentationRuleRequestDTO("plan", "equals", "pro"));

    var response = define(new DefineTriggerRequestDTO("app.opened", START, null, 1.0));

    assertThat(response.eventName()).isEqualTo("app.opened");
    assertThat(response.rules()).hasSize(1);
    assertThat(storedVersion().getTriggerEventName()).isEqualTo("app.opened");
    assertThat(storedVersion().getRules()).hasSize(1);
  }

  @Test
  @DisplayName("Cada entrada incoerente devolve 400 e nada é gravado")
  void recusa_disparo_incoerente() {
    var casos =
        java.util.List.of(
            new DefineTriggerRequestDTO("checkout.completed", START, START, 0.25),
            new DefineTriggerRequestDTO("checkout.completed", START, START.minusSeconds(1), 0.25),
            new DefineTriggerRequestDTO("checkout.completed", START, null, 1.5),
            new DefineTriggerRequestDTO("checkout.completed", START, null, -0.1),
            new DefineTriggerRequestDTO("   ", START, null, 0.25),
            new DefineTriggerRequestDTO("Checkout Completed", START, null, 0.25));

    for (var caso : casos) {
      client
          .put()
          .uri(uri())
          .contentType(MediaType.APPLICATION_JSON)
          .body(caso)
          .exchange()
          .expectStatus()
          .isBadRequest();
    }

    assertThat(storedVersion().getTriggerEventName()).isNull();
  }

  private SegmentationRuleResponseDTO addRule(AddSegmentationRuleRequestDTO request) {
    return client
        .post()
        .uri(uri() + "/rules")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SegmentationRuleResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Acrescenta e remove regras, deixando as demais intactas")
  void acrescenta_e_remove_regras() {
    define(new DefineTriggerRequestDTO("checkout.completed", START, null, 0.25));

    var first = addRule(new AddSegmentationRuleRequestDTO("plan", "equals", "pro"));
    addRule(new AddSegmentationRuleRequestDTO("country", "present", null));

    assertThat(first.attribute()).isEqualTo("plan");
    assertThat(first.operation()).isEqualTo("equals");
    assertThat(first.value()).isEqualTo("pro");
    assertThat(storedVersion().getRules()).hasSize(2);

    client.delete().uri(uri() + "/rules/" + first.id()).exchange().expectStatus().isNoContent();

    assertThat(storedVersion().getRules())
        .extracting(SegmentationRuleJpaEntity::getAttribute)
        .containsExactly("country");
  }

  @Test
  @DisplayName("Acrescentar regra sem disparo definido devolve 422")
  void recusa_regra_sem_disparo() {
    client
        .post()
        .uri(uri() + "/rules")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new AddSegmentationRuleRequestDTO("plan", "present", null))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("trigger.not_defined");

    assertThat(storedVersion().getRules()).isEmpty();
  }

  @Test
  @DisplayName("Regra incoerente com a operação devolve 400")
  void recusa_regra_incoerente() {
    define(new DefineTriggerRequestDTO("checkout.completed", START, null, 0.25));

    for (var caso :
        java.util.List.of(
            new AddSegmentationRuleRequestDTO("plan", "equals", null),
            new AddSegmentationRuleRequestDTO("plan", "present", "pro"),
            new AddSegmentationRuleRequestDTO("   ", "present", null))) {
      client
          .post()
          .uri(uri() + "/rules")
          .contentType(MediaType.APPLICATION_JSON)
          .body(caso)
          .exchange()
          .expectStatus()
          .isBadRequest();
    }

    assertThat(storedVersion().getRules()).isEmpty();
  }

  @Test
  @DisplayName("Regra inexistente devolve 404")
  void recusa_remover_regra_desconhecida() {
    define(new DefineTriggerRequestDTO("checkout.completed", START, null, 0.25));

    client
        .delete()
        .uri(uri() + "/rules/" + UUID.randomUUID())
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("segmentation_rule.not_found");
  }

  @Test
  @DisplayName("Fora do escopo da aplicação é 404 em todas as escritas")
  void recusa_fora_do_escopo() {
    var outra =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("outra-app").build()));
    var alheia = "/applications/" + outra.getId() + "/surveys/" + survey.id() + "/trigger";

    client
        .put()
        .uri(alheia)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DefineTriggerRequestDTO("checkout.completed", START, null, 0.25))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");

    assertThat(storedVersion().getTriggerEventName()).isNull();
  }
}
