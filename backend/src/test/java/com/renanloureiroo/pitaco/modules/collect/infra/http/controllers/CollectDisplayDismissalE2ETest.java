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
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.AnswerDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Dispensa da exibição")
class CollectDisplayDismissalE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String DISPLAYS = "/collect/displays";

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
  private String displayId;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    ApplicationId applicationId = application.id();

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
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?"))
            .triggeredBy(TriggerFactory.aTrigger().forEvent("checkout.completed").withRate(1.0))
            .buildPublishedSavedIn(versions);

    displayId = UUID.randomUUID().toString();
    open(displayId);
  }

  @Test
  @DisplayName("Dispensar depois de responder a primeira preserva o que já veio (SC-011)")
  void dispensa_preserva_o_parcial() {
    submit(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.DISMISSED,
                List.of(new AnswerDTO(questionKeyOf(0), AnswerStatus.ANSWERED, "achei caro"))))
        .expectStatus()
        .isNoContent();

    assertThat(outcome()).isEqualTo("DISMISSED");
    assertThat(closedAt()).isNotNull();
    assertThat(countOf("survey_answers")).isEqualTo(1);
    assertThat(
            jdbc.sql("select text_value from survey_answers where display_id = :id")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo("achei caro");
  }

  @Test
  @DisplayName("A dispensa não exige a obrigatória: sem nenhuma resposta também vale")
  void dispensa_sem_resposta_e_aceita() {
    submit(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.DISMISSED, List.of()))
        .expectStatus()
        .isNoContent();

    assertThat(outcome()).isEqualTo("DISMISSED");
    assertThat(countOf("survey_answers")).isZero();
    assertThat(
            jdbc.sql("select version_id from survey_displays where id = :id")
                .param("id", displayId)
                .query(String.class)
                .single())
        .isEqualTo(published.id().value());
  }

  @Test
  @DisplayName("Nova submissão na exibição dispensada devolve 409 e o desfecho permanece")
  void nova_submissao_na_dispensada_e_409() {
    submit(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.DISMISSED, List.of()))
        .expectStatus()
        .isNoContent();

    submit(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.DISMISSED,
                List.of(new AnswerDTO(questionKeyOf(0), AnswerStatus.ANSWERED, "agora vai"))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("display.already_closed");

    assertThat(outcome()).isEqualTo("DISMISSED");
    assertThat(countOf("survey_answers")).isZero();
  }

  @Test
  @DisplayName("Concluir uma exibição dispensada devolve 409, e o desfecho permanece")
  void concluir_a_dispensada_e_409() {
    submit(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.DISMISSED, List.of()))
        .expectStatus()
        .isNoContent();
    var fechamento = closedAt();

    submit(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.COMPLETED,
                List.of(
                    new AnswerDTO(questionKeyOf(0), AnswerStatus.ANSWERED, "achei caro"),
                    new AnswerDTO(questionKeyOf(1), AnswerStatus.ANSWERED, 9))))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("display.already_closed");

    assertThat(outcome()).isEqualTo("DISMISSED");
    assertThat(closedAt()).isEqualTo(fechamento);
  }

  @Test
  @DisplayName("Dispensar uma exibição concluída devolve 409")
  void dispensar_a_concluida_e_409() {
    submit(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.COMPLETED,
                List.of(
                    new AnswerDTO(questionKeyOf(0), AnswerStatus.ANSWERED, "achei caro"),
                    new AnswerDTO(questionKeyOf(1), AnswerStatus.ANSWERED, 9))))
        .expectStatus()
        .isNoContent();

    submit(new SubmissionRequestDTO(SubmissionRequestDTO.Outcome.DISMISSED, List.of()))
        .expectStatus()
        .isEqualTo(409);

    assertThat(outcome()).isEqualTo("COMPLETED");
  }

  private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec submit(
      SubmissionRequestDTO request) {
    return client
        .post()
        .uri(DISPLAYS + "/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange();
  }

  private void open(String id) {
    client
        .post()
        .uri(DISPLAYS)
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                id, surveyId, published.id().value(), new RespondentDTO("u-8f1c", null), null, null))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private String questionKeyOf(int index) {
    return published.getQuestions().stream()
        .sorted(Comparator.comparingInt(Question::getPosition))
        .toList()
        .get(index)
        .getKey()
        .value();
  }

  private String outcome() {
    return jdbc
        .sql("select outcome from survey_displays where id = :id")
        .param("id", displayId)
        .query(String.class)
        .single();
  }

  private Instant closedAt() {
    return jdbc
        .sql("select closed_at from survey_displays where id = :id")
        .param("id", displayId)
        .query(Instant.class)
        .single();
  }

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}
