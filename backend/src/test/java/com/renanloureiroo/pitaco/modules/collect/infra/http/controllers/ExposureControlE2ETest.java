package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.AnswerDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.QuotaProgressResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyStateTransitionResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@DisplayName("Controle de exposição de ponta a ponta")
class ExposureControlE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String EVENT = "checkout.completed";
  private static final Instant PUBLISHED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private String key;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();
  }

  private SurveyVersion published(SurveyFactory factory, Instant publishedAt) {
    var survey = factory.forApplication(applicationId).buildSavedIn(surveys);
    return SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger().forEvent(EVENT).withRate(1.0))
        .publishedAt(publishedAt)
        .buildPublishedSavedIn(versions);
  }

  private EligibilityResponseDTO.SurveyDTO eligible(String reference, Map<String, String> attrs) {
    return client
        .post()
        .uri("/collect/eligibility")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new EligibilityRequestDTO(
                EVENT, new RespondentDTO(reference, null), attrs.isEmpty() ? null : attrs))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EligibilityResponseDTO.class)
        .returnResult()
        .getResponseBody()
        .survey();
  }

  private String open(SurveyVersion version, String reference) {
    var displayId = UUID.randomUUID().toString();
    client
        .post()
        .uri("/collect/displays")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                displayId,
                version.getSurveyId().value(),
                version.id().value(),
                new RespondentDTO(reference, null),
                Map.of(),
                "1.0.0"))
        .exchange()
        .expectStatus()
        .isCreated();
    return displayId;
  }

  private void complete(String displayId, SurveyVersion version) {
    client
        .post()
        .uri("/collect/displays/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.COMPLETED,
                List.of(
                    new AnswerDTO(
                        version.getQuestions().getFirst().getKey().value(),
                        AnswerStatus.ANSWERED,
                        "ok"))))
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private String surveyUri(SurveyVersion version) {
    return "/applications/"
        + applicationId.value()
        + "/surveys/"
        + version.getSurveyId().value();
  }

  private QuotaProgressResponseDTO quotaProgress(SurveyVersion version) {
    return client
        .get()
        .uri(surveyUri(version) + "/quota-progress")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(QuotaProgressResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private String lifecycleOf(SurveyVersion version) {
    return jdbc.sql("select lifecycle from surveys where id = :id")
        .param("id", version.getSurveyId().value())
        .query(String.class)
        .single();
  }

  @Test
  @DisplayName("O descanso vale entre pesquisas diferentes, e a isenta passa por cima dele")
  void descanso_entre_pesquisas() {
    jdbc.sql("update applications set quiet_period_days = 7 where id = :id")
        .param("id", applicationId.value())
        .update();
    var prioritaria = published(SurveyFactory.aPublishedSurvey().withPriority(10), PUBLISHED_AT);
    var outra = published(SurveyFactory.aPublishedSurvey(), PUBLISHED_AT);

    assertThat(eligible("u-1", Map.of()).surveyId())
        .isEqualTo(prioritaria.getSurveyId().value());
    complete(open(prioritaria, "u-1"), prioritaria);

    assertThat(eligible("u-1", Map.of()))
        .describedAs("a outra pesquisa ficaria elegível, mas o respondente está descansando")
        .isNull();
    assertThat(eligible("u-2", Map.of()).surveyId())
        .describedAs("o descanso é de quem viu, não da pesquisa")
        .isEqualTo(prioritaria.getSurveyId().value());

    client
        .patch()
        .uri(surveyUri(outra))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"ignoresQuietPeriod\":true}")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(eligible("u-1", Map.of()).surveyId()).isEqualTo(outra.getSurveyId().value());
  }

  @Test
  @DisplayName("Prioridade maior vence mesmo publicada depois, e a escolha é estável")
  void prioridade_vence() {
    published(SurveyFactory.aPublishedSurvey(), PUBLISHED_AT);
    var prioritaria =
        published(SurveyFactory.aPublishedSurvey().withPriority(10), PUBLISHED_AT.plusSeconds(3600));

    assertThat(eligible("u-1", Map.of()).surveyId())
        .isEqualTo(prioritaria.getSurveyId().value());
    assertThat(eligible("u-1", Map.of()).surveyId())
        .isEqualTo(prioritaria.getSurveyId().value());
  }

  @Test
  @DisplayName("A cota encerra a pesquisa sozinha, e a sessão já aberta ainda conclui")
  void ciclo_da_cota() {
    var version = published(SurveyFactory.aPublishedSurvey().withResponseQuota(2), PUBLISHED_AT);
    var primeira = open(version, "u-1");
    var segunda = open(version, "u-2");
    var aberta = open(version, "u-3");

    complete(primeira, version);
    assertThat(quotaProgress(version))
        .isEqualTo(new QuotaProgressResponseDTO(2, 1));
    assertThat(lifecycleOf(version)).isEqualTo("PUBLISHED");

    complete(segunda, version);
    assertThat(lifecycleOf(version)).isEqualTo("ENDED");
    assertThat(eligible("u-4", Map.of())).isNull();

    complete(aberta, version);
    assertThat(quotaProgress(version))
        .describedAs("o total passa um pouco da cota, de propósito")
        .isEqualTo(new QuotaProgressResponseDTO(2, 3));

    var transitions =
        client
            .get()
            .uri(surveyUri(version) + "/transitions")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<SurveyStateTransitionResponseDTO>>() {})
            .returnResult()
            .getResponseBody();
    assertThat(transitions)
        .filteredOn(transition -> transition.reason().equals("quota_reached"))
        .singleElement()
        .satisfies(
            transition -> {
              assertThat(transition.from()).isEqualTo("active");
              assertThat(transition.to()).isEqualTo("ended");
            });
    assertThat(transitions)
        .extracting(SurveyStateTransitionResponseDTO::reason)
        .doesNotContain("manual_end");
  }

  @Test
  @DisplayName("Sem cota, o progresso só conta; pesquisa desconhecida é 404 e chave do SDK é 403")
  void progresso_da_cota() {
    var version = published(SurveyFactory.aPublishedSurvey(), PUBLISHED_AT);
    complete(open(version, "u-1"), version);

    assertThat(quotaProgress(version)).isEqualTo(new QuotaProgressResponseDTO(null, 1));

    client
        .get()
        .uri("/applications/" + applicationId.value() + "/surveys/" + UUID.randomUUID() + "/quota-progress")
        .exchange()
        .expectStatus()
        .isNotFound();
    client
        .get()
        .uri(surveyUri(version) + "/quota-progress")
        .header(HEADER, key)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  @DisplayName("Os atributos da consulta alimentam o catálogo da aplicação")
  void atributos_alimentam_o_catalogo() {
    eligible("u-1", Map.of("plano", "pro"));
    eligible("u-2", Map.of("plano", "free"));

    assertThat(
            jdbc.sql(
                    "select v.value from application_attribute_values v"
                        + " join application_attributes a on a.id = v.attribute_id"
                        + " where a.application_id = :app and a.name = 'plano' order by v.value")
                .param("app", applicationId.value())
                .query(String.class)
                .list())
        .containsExactly("free", "pro");
  }

  private long quotaTransitions(SurveyVersion version) {
    return jdbc.sql(
            "select count(*) from survey_state_transitions"
                + " where survey_id = :id and upper(reason) = 'QUOTA_REACHED'")
        .param("id", version.getSurveyId().value())
        .query(Long.class)
        .single();
  }

  @RepeatedTest(3)
  @DisplayName("Duas conclusões simultâneas que atingem a cota encerram a pesquisa uma vez só")
  void conclusoes_simultaneas_encerram_uma_vez() throws Exception {
    var version = published(SurveyFactory.aPublishedSurvey().withResponseQuota(2), PUBLISHED_AT);
    var abertas = List.of(open(version, "u-1"), open(version, "u-2"));
    var largada = new CountDownLatch(1);

    try (var pool = Executors.newFixedThreadPool(abertas.size())) {
      var envios =
          abertas.stream()
              .map(
                  displayId ->
                      pool.submit(
                          () -> {
                            largada.await();
                            complete(displayId, version);
                            return null;
                          }))
              .toList();
      largada.countDown();
      for (var envio : envios) {
        envio.get(30, TimeUnit.SECONDS);
      }
    }

    assertThat(lifecycleOf(version))
        .describedAs("nenhuma das duas pode deixar de enxergar a outra na contagem")
        .isEqualTo("ENDED");
    assertThat(quotaTransitions(version)).isEqualTo(1);
  }

  private int pause(SurveyVersion version) {
    return client
        .post()
        .uri(surveyUri(version) + "/pause")
        .exchange()
        .expectBody()
        .returnResult()
        .getStatus()
        .value();
  }

  // A pausa pode chegar antes ou depois da conclusão; o que não pode é gravar a pesquisa inteira
  // por cima de um encerramento por cota e deixá-la pausada com a cota atingida.
  @RepeatedTest(5)
  @DisplayName("Pausa manual e conclusão que atinge a cota, juntas, terminam sempre encerradas")
  void pausa_e_cota_simultaneas() throws Exception {
    var version = published(SurveyFactory.aPublishedSurvey().withResponseQuota(1), PUBLISHED_AT);
    var aberta = open(version, "u-1");
    var largada = new CountDownLatch(1);

    try (var pool = Executors.newFixedThreadPool(2)) {
      var conclusao =
          pool.submit(
              () -> {
                largada.await();
                complete(aberta, version);
                return null;
              });
      var pausa =
          pool.submit(
              () -> {
                largada.await();
                return pause(version);
              });
      largada.countDown();

      conclusao.get(30, TimeUnit.SECONDS);
      assertThat(pausa.get(30, TimeUnit.SECONDS))
          .describedAs("pausa antes da cota passa; depois dela é transição recusada")
          .isIn(200, 422);
    }

    assertThat(lifecycleOf(version)).isEqualTo("ENDED");
    assertThat(quotaTransitions(version)).isEqualTo(1);
  }

  @Test
  @DisplayName("Reduzir a cota até o total concluído encerra a pesquisa na hora")
  void cota_reduzida_encerra_na_hora() {
    var version = published(SurveyFactory.aPublishedSurvey().withResponseQuota(10), PUBLISHED_AT);
    complete(open(version, "u-1"), version);

    client
        .patch()
        .uri(surveyUri(version))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("responseQuota", 1))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.state")
        .isEqualTo("ended")
        .jsonPath("$.responseQuota")
        .isEqualTo(1);

    assertThat(lifecycleOf(version)).isEqualTo("ENDED");
    assertThat(quotaTransitions(version)).isEqualTo(1);
    assertThat(eligible("u-2", Map.of())).isNull();
  }
}
