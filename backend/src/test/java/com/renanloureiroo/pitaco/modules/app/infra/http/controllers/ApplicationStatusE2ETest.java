package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("POST /applications/{applicationId}/deactivate e /activate")
class ApplicationStatusE2ETest {

  private static final String EVENT = "checkout.completed";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;
  private String key;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(
            ApplicationJpaMapper.toJpa(ApplicationFactory.anApplicationWithPolicies().build()));

    var issued = ApiKey.issue(ApplicationId.of(application.getId()), ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();
  }

  private String uri(String action) {
    return "/applications/" + application.getId() + "/" + action;
  }

  private ApplicationJpaEntity reload() {
    return applications.findById(application.getId()).orElseThrow();
  }

  private void publishedSurvey() {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(ApplicationId.of(application.getId()))
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);

    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.anNpsQuestion().withStatement("Recomendaria?"))
        .triggeredBy(TriggerFactory.aTrigger().forEvent(EVENT).withRate(1.0))
        .buildPublishedSavedIn(versions);
  }

  private RestTestClient.ResponseSpec eligibility() {
    return client
        .post()
        .uri("/collect/eligibility")
        .header("X-Pitaco-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EligibilityRequestDTO(EVENT, new RespondentDTO("u-1", null), null))
        .exchange();
  }

  @Test
  @DisplayName("Desativa, responde 200 com o estado e persiste; políticas seguem intactas")
  void desativa() {
    client
        .post()
        .uri(uri("deactivate"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("inactive")
        .jsonPath("$.quietPeriodDays")
        .isEqualTo(15);

    var saved = reload();
    assertThat(saved.getStatus()).isEqualTo(Status.INACTIVE);
    assertThat(saved.getRetentionDays()).isEqualTo(180);
  }

  @Test
  @DisplayName("Desativar de novo é idempotente: 200 e o updatedAt não anda")
  void desativar_e_idempotente() {
    client.post().uri(uri("deactivate")).exchange().expectStatus().isOk();
    var updatedAt = reload().getUpdatedAt();

    client
        .post()
        .uri(uri("deactivate"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("inactive");

    assertThat(reload().getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("Reativa a aplicação inativa; reativar o já ativo é idempotente")
  void reativa() {
    client.post().uri(uri("deactivate")).exchange().expectStatus().isOk();

    client
        .post()
        .uri(uri("activate"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("active");
    assertThat(reload().getStatus()).isEqualTo(Status.ACTIVE);

    var updatedAt = reload().getUpdatedAt();
    client.post().uri(uri("activate")).exchange().expectStatus().isOk();
    assertThat(reload().getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("Aplicação inativa entrega silêncio ao SDK e volta a entregar ao ser reativada")
  void inativa_entrega_silencio_e_reativada_volta_a_entregar() {
    publishedSurvey();

    eligibility().expectStatus().isOk().expectBody().jsonPath("$.survey").exists();

    client.post().uri(uri("deactivate")).exchange().expectStatus().isOk();
    eligibility().expectStatus().isOk().expectBody().jsonPath("$.survey").doesNotExist();

    client.post().uri(uri("activate")).exchange().expectStatus().isOk();
    eligibility().expectStatus().isOk().expectBody().jsonPath("$.survey").exists();
  }

  @Test
  @DisplayName("O histórico da aplicação inativa continua legível no painel")
  void historico_continua_legivel() {
    publishedSurvey();
    client.post().uri(uri("deactivate")).exchange().expectStatus().isOk();

    client
        .get()
        .uri("/applications/" + application.getId())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("inactive");

    client
        .get()
        .uri("/applications/" + application.getId() + "/surveys")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Identificador inexistente e malformado respondem 404 nas duas ações")
  void recusa_inexistente_e_malformado() {
    for (var action : new String[] {"deactivate", "activate"}) {
      for (var id : new String[] {UUID.randomUUID().toString(), "nao-e-um-id"}) {
        client
            .post()
            .uri("/applications/" + id + "/" + action)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("application.not_found");
      }
    }

    assertThat(reload().getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  @DisplayName("A chave do SDK não vale no painel: 403 e nada muda")
  void recusa_chave_de_aplicacao() {
    client
        .post()
        .uri(uri("deactivate"))
        .header("X-Pitaco-Key", key)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");

    assertThat(reload().getStatus()).isEqualTo(Status.ACTIVE);
  }
}
