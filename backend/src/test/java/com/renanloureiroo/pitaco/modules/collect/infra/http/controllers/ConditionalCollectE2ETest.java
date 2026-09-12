package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
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
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Coleta de pesquisa com condição de exibição")
class ConditionalCollectE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String DISPLAYS = "/collect/displays";
  private static final String EVENT = "checkout.completed";
  private static final String REFERENCE = "u-cond";

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private String key;
  private String surveyId;
  private SurveyVersion published;
  private QuestionKey choice;
  private QuestionKey followUp;
  private QuestionKey nps;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));

    var issued = ApiKey.issue(application.id(), ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(application.id())
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    surveyId = survey.id().value();

    var version = SurveyVersion.create(survey.id(), 1);
    choice =
        version
            .addQuestion(QuestionFactory.aSingleChoiceQuestion().withStatement("Gostou?").asDraft())
            .getKey();
    followUp =
        version
            .addQuestion(
                QuestionFactory.aFreeTextQuestion()
                    .withStatement("O que faltou?")
                    .conditionedOn(
                        DisplayCondition.of(choice, ConditionOperator.EQUALS, List.of("no")))
                    .asDraft())
            .getKey();
    nps =
        version
            .addQuestion(
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?").optional().asDraft())
            .getKey();
    version.defineTrigger(TriggerFactory.aTrigger().forEvent(EVENT).withRate(1.0).build());
    published =
        versions.create(
            version.publish(Instant.now(), Optional.empty(), Optional.empty(), Optional.empty()));
  }

  private void open(String displayId) {
    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                displayId,
                surveyId,
                published.id().value(),
                new RespondentDTO(REFERENCE, null),
                Map.of(),
                "1.5.0"))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private RestTestClient.ResponseSpec submit(String displayId, List<AnswerDTO> answers) {
    return client
        .post()
        .uri(DISPLAYS + "/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.COMPLETED, answers))
        .exchange();
  }

  private String statusOf(String displayId, QuestionKey question) {
    return jdbc.sql(
            "select status from survey_answers where display_id = :id and question_key = :key")
        .param("id", displayId)
        .param("key", question.value())
        .query(String.class)
        .single();
  }

  @Test
  @DisplayName("A pesquisa entregue ao SDK leva a condição junto da pergunta condicionada")
  void elegibilidade_entrega_a_condicao() {
    var body =
        client
            .post()
            .uri("/collect/eligibility")
            .header(HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new EligibilityRequestDTO(EVENT, new RespondentDTO(REFERENCE, null), null))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(EligibilityResponseDTO.class)
            .returnResult()
            .getResponseBody();

    var questions = body.survey().questions();
    assertThat(questions.get(0).condition()).isNull();
    assertThat(questions.get(1).condition())
        .satisfies(
            condition -> {
              assertThat(condition.sourceKey()).isEqualTo(choice.value());
              assertThat(condition.operator()).isEqualTo("equals");
              assertThat(condition.values()).containsExactly("no");
              assertThat(condition.min()).isNull();
            });
    assertThat(questions.get(2).condition()).isNull();
    assertThat(questions.get(2).range().max()).isEqualTo(10);
  }

  @Test
  @DisplayName("Obrigatória pulada pela condição vem como não aplicável, e a conclusão é aceita")
  void nao_aplicavel_na_condicionada_e_aceito() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    submit(
            displayId,
            List.of(
                new AnswerDTO(choice.value(), AnswerStatus.ANSWERED, "yes"),
                new AnswerDTO(followUp.value(), AnswerStatus.NOT_APPLICABLE, null)))
        .expectStatus()
        .isNoContent();

    assertThat(statusOf(displayId, followUp)).isEqualTo("NOT_APPLICABLE");
    assertThat(
            jdbc.sql("select outcome from survey_displays where id = :id")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Não aplicável em pergunta sem condição é 422, e nada é gravado")
  void nao_aplicavel_sem_condicao_e_recusado() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    submit(
            displayId,
            List.of(
                new AnswerDTO(choice.value(), AnswerStatus.ANSWERED, "yes"),
                new AnswerDTO(followUp.value(), AnswerStatus.NOT_APPLICABLE, null),
                new AnswerDTO(nps.value(), AnswerStatus.NOT_APPLICABLE, null)))
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("submission.rejected")
        .jsonPath("$.errors.length()")
        .isEqualTo(1)
        .jsonPath("$.errors[0].code")
        .isEqualTo("answer.not_applicable_unconditional")
        .jsonPath("$.errors[0].questionKey")
        .isEqualTo(nps.value());

    assertThat(jdbc.sql("select count(*) from survey_answers").query(Long.class).single()).isZero();
    assertThat(
            jdbc.sql("select outcome from survey_displays where id = :id")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo("STARTED");
  }
}
