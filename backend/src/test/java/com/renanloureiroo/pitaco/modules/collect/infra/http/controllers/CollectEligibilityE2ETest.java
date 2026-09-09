package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("POST /collect/eligibility")
class CollectEligibilityE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String ELIGIBILITY = "/collect/eligibility";
  private static final String EVENT = "checkout.completed";
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

  @Test
  @DisplayName("A pesquisa publicada chega inteira, com as perguntas na ordem definida")
  void caminho_feliz() {
    var version = publishedSurvey();

    var body = eligibility(request(Map.of()));

    var survey = body.survey();
    assertThat(survey).isNotNull();
    assertThat(survey.versionId()).isEqualTo(version);
    assertThat(survey.questions()).hasSize(2);

    var primeira = survey.questions().getFirst();
    assertThat(primeira.position()).isEqualTo(1);
    assertThat(primeira.type()).isEqualTo(QuestionType.SINGLE_CHOICE);
    assertThat(primeira.required()).isTrue();
    assertThat(primeira.options()).hasSize(2);
    assertThat(primeira.range()).isNull();

    var segunda = survey.questions().getLast();
    assertThat(segunda.position()).isEqualTo(2);
    assertThat(segunda.type()).isEqualTo(QuestionType.NPS);
    assertThat(segunda.range()).isNotNull();
    assertThat(segunda.range().min()).isZero();
    assertThat(segunda.range().max()).isEqualTo(10);
  }

  @Test
  @DisplayName("Evento sem pesquisa devolve survey nulo e não grava nada")
  void evento_sem_pesquisa() {
    publishedSurvey();

    assertNothingWritten(
        () ->
            assertThat(
                    eligibility(
                            new EligibilityRequestDTO(
                                "outro.evento", new RespondentDTO(REFERENCE, null), null))
                        .survey())
                .isNull());
  }

  @Test
  @DisplayName("Pesquisa pausada devolve survey nulo e não grava nada")
  void pesquisa_pausada() {
    publishedSurveyIn(SurveyLifecycle.PAUSED);

    assertNothingWritten(() -> assertThat(eligibility(request(Map.of())).survey()).isNull());
  }

  @Test
  @DisplayName("Pesquisa encerrada devolve survey nulo")
  void pesquisa_encerrada() {
    publishedSurveyIn(SurveyLifecycle.ENDED);

    assertNothingWritten(() -> assertThat(eligibility(request(Map.of())).survey()).isNull());
  }

  @Test
  @DisplayName("Antes e depois da janela devolve survey nulo")
  void fora_da_janela() {
    var survey = savedSurvey(applicationId, SurveyLifecycle.PUBLISHED);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(
            TriggerFactory.aTrigger()
                .forEvent(EVENT)
                .withRate(1.0)
                .startingIn(1, ChronoUnit.HOURS)
                .endingIn(2, ChronoUnit.HOURS))
        .buildPublishedSavedIn(versions);

    assertNothingWritten(() -> assertThat(eligibility(request(Map.of())).survey()).isNull());

    database.clean();
    setUp();

    var outra = savedSurvey(applicationId, SurveyLifecycle.PUBLISHED);
    var now = Instant.now();
    SurveyVersionFactory.aVersion()
        .forSurvey(outra.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(
            TriggerFactory.aTrigger()
                .forEvent(EVENT)
                .withRate(1.0)
                .startingAt(now.minus(2, ChronoUnit.HOURS))
                .endingAt(now.minusSeconds(60)))
        .buildPublishedSavedIn(versions);

    assertNothingWritten(() -> assertThat(eligibility(request(Map.of())).survey()).isNull());
  }

  @Test
  @DisplayName("Conteúdo de rascunho nunca é entregue")
  void rascunho_nunca_e_entregue() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .buildSavedIn(versions);

    assertNothingWritten(() -> assertThat(eligibility(request(Map.of())).survey()).isNull());
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação nunca é alcançada")
  void pesquisa_de_outra_aplicacao() {
    var outra = ApplicationFactory.anApplication().withSlug("outra-app").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));

    var survey = savedSurvey(outra.id(), SurveyLifecycle.PUBLISHED);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .buildPublishedSavedIn(versions);

    assertThat(eligibility(request(Map.of())).survey()).isNull();
  }

  @Test
  @DisplayName("As quatro operações de segmentação, e o atributo ausente falhando fechado")
  void segmentacao_nas_quatro_operacoes() {
    publishedSurveyRuledBy(rule(RuleOperation.EQUALS, "premium"));
    assertThat(eligibility(request(Map.of("plano", "premium"))).survey()).isNotNull();
    assertThat(eligibility(request(Map.of("plano", "free"))).survey()).isNull();
    assertThat(eligibility(request(Map.of())).survey()).isNull();

    resetWith(rule(RuleOperation.NOT_EQUALS, "free"));
    assertThat(eligibility(request(Map.of("plano", "premium"))).survey()).isNotNull();
    assertThat(eligibility(request(Map.of("plano", "free"))).survey()).isNull();
    assertThat(eligibility(request(Map.of())).survey())
        .describedAs("diferença sobre atributo ausente falha fechado")
        .isNull();

    resetWith(rule(RuleOperation.PRESENT, null));
    assertThat(eligibility(request(Map.of("plano", "qualquer"))).survey()).isNotNull();
    assertThat(eligibility(request(Map.of())).survey()).isNull();

    resetWith(rule(RuleOperation.ABSENT, null));
    assertThat(eligibility(request(Map.of())).survey()).isNotNull();
    assertThat(eligibility(request(Map.of("plano", "premium"))).survey()).isNull();
  }

  @Test
  @DisplayName("Duas pesquisas no mesmo evento devolvem exatamente uma, e sempre a mesma")
  void duas_pesquisas_disputando_o_mesmo_evento() {
    var primeiraPublicacao = Instant.parse("2026-01-01T00:00:00Z");
    var maisAntiga = savedSurvey(applicationId, SurveyLifecycle.PUBLISHED);
    var versaoAntiga =
        SurveyVersionFactory.aVersion()
            .forSurvey(maisAntiga.id())
            .withQuestions(QuestionFactory.aFreeTextQuestion())
            .triggeredBy(openTrigger())
            .publishedAt(primeiraPublicacao)
            .buildPublishedSavedIn(versions);

    var maisNova = savedSurvey(applicationId, SurveyLifecycle.PUBLISHED);
    SurveyVersionFactory.aVersion()
        .forSurvey(maisNova.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .publishedAt(primeiraPublicacao.plusSeconds(3600))
        .buildPublishedSavedIn(versions);

    var primeira = eligibility(request(Map.of())).survey();
    var segunda = eligibility(request(Map.of())).survey();

    assertThat(primeira).isNotNull();
    assertThat(primeira.versionId()).isEqualTo(versaoAntiga.id().value());
    assertThat(segunda.versionId()).isEqualTo(primeira.versionId());
  }

  @Test
  @DisplayName("Corpo malformado devolve 400")
  void json_malformado_e_400() {
    client
        .post()
        .uri(ELIGIBILITY)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"event\": ")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }

  @Test
  @DisplayName("Respondente sem nenhuma identificação devolve 400")
  void respondente_sem_identificacao_e_400() {
    client
        .post()
        .uri(ELIGIBILITY)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EligibilityRequestDTO(EVENT, new RespondentDTO(null, null), null))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid");
  }

  private EligibilityResponseDTO eligibility(EligibilityRequestDTO request) {
    var body =
        client
            .post()
            .uri(ELIGIBILITY)
            .header(HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(EligibilityResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(body).isNotNull();
    return body;
  }

  // SC-003 ao pé da letra: contagem de linhas nas três tabelas de coleta, antes e depois.
  private void assertNothingWritten(Runnable call) {
    var before = collectRowCount();
    call.run();
    assertThat(collectRowCount()).isEqualTo(before);
  }

  private long collectRowCount() {
    return jdbc
        .sql(
            """
            select (select count(*) from respondents)
                 + (select count(*) from survey_displays)
                 + (select count(*) from survey_answers)
            """)
        .query(Long.class)
        .single();
  }

  private void resetWith(SegmentationRule rule) {
    database.clean();
    setUp();
    publishedSurveyRuledBy(rule);
  }

  private static SegmentationRule rule(RuleOperation operation, String value) {
    return new SegmentationRule(
        com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId.generate(),
        "plano",
        operation,
        Optional.ofNullable(value));
  }

  private String publishedSurvey() {
    var survey = savedSurvey(applicationId, SurveyLifecycle.PUBLISHED);

    return SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(
            QuestionFactory.aSingleChoiceQuestion().withStatement("O que achou do checkout?"),
            QuestionFactory.anNpsQuestion().withStatement("De 0 a 10, quanto recomendaria?"))
        .triggeredBy(openTrigger())
        .buildPublishedSavedIn(versions)
        .id()
        .value();
  }

  private void publishedSurveyIn(SurveyLifecycle lifecycle) {
    var survey = savedSurvey(applicationId, lifecycle);

    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .buildPublishedSavedIn(versions);
  }

  private void publishedSurveyRuledBy(SegmentationRule rule) {
    var survey = savedSurvey(applicationId, SurveyLifecycle.PUBLISHED);

    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .ruledBy(rule)
        .buildPublishedSavedIn(versions);
  }

  private Survey savedSurvey(ApplicationId owner, SurveyLifecycle lifecycle) {
    return SurveyFactory.aSurvey()
        .forApplication(owner)
        .published(1)
        .inLifecycle(lifecycle)
        .buildSavedIn(surveys);
  }

  private static TriggerFactory openTrigger() {
    return TriggerFactory.aTrigger().forEvent(EVENT).withRate(1.0);
  }

  private EligibilityRequestDTO request(Map<String, String> attributes) {
    return new EligibilityRequestDTO(
        EVENT, new RespondentDTO(REFERENCE, null), attributes.isEmpty() ? null : attributes);
  }
}
