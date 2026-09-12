package com.renanloureiroo.pitaco.modules.privacy.infra.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// O schema, e não o código, é quem garante que nada fica órfão: aqui o DELETE é SQL cru, sem
// passar por caso de uso nenhum.
@E2E
@DisplayName("Cascatas de exclusão no schema")
class CascadeDeletionE2ETest {

  private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

  private static final List<String> APPLICATION_TABLES =
      List.of(
          "api_keys", "surveys", "survey_versions", "questions", "question_options",
          "segmentation_rules", "survey_state_transitions", "respondents", "survey_displays",
          "survey_display_attributes", "survey_answers", "survey_answer_options",
          "application_events", "application_attributes", "application_attribute_values",
          "sdk_version_usage", "sdk_version_daily_usage", "suppression_events",
          "sdk_error_reports", "deletion_audits", "aggregate_snapshots",
          "aggregate_snapshot_counts", "retention_runs");

  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired SurveyDisplayRepository displays;
  @Autowired RespondentRepository respondents;
  @Autowired AnswerRepository answers;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;
  private RespondentId respondentId;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
    apiKeys.save(ApiKeyJpaMapper.toJpa(ApiKey.issue(applicationId, ApiKeyLabel.of("iOS")).apiKey()));

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    surveyId = survey.id();
    var version =
        SurveyVersionFactory.aVersion()
            .forSurvey(surveyId)
            .withQuestions(QuestionFactory.aSingleChoiceQuestion(), QuestionFactory.anNpsQuestion())
            .buildPublishedSavedIn(versions);
    versionId = version.id();
    QuestionKey choice = version.getQuestions().getFirst().getKey();

    respondentId =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference("u-1")
            .buildSavedIn(respondents)
            .id();
    var display =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .forRespondent(respondentId)
            .withAttributes(Map.of("plano", "pro"))
            .openedAt(AT)
            .completedAt(AT.plusSeconds(30))
            .buildSavedIn(displays)
            .id();
    var option = version.getQuestions().getFirst().getOptions().getFirst().value();
    AnswerFactory.anAnswer().forDisplay(display).forQuestion(choice).withOptions(option).buildSavedIn(answers);

    insert(
        "insert into suppression_events (id, application_id, survey_id, version_id, respondent_id,"
            + " reason, min_required_version, occurred_at) values (?, ?, ?, ?, ?,"
            + " 'UNKNOWN_QUESTION_TYPE', '1.0.0', now())",
        id(), applicationId.value(), surveyId.value(), versionId.value(), respondentId.value());
    insert(
        "insert into application_events (id, application_id, name, first_seen_at, last_seen_at)"
            + " values (?, ?, 'checkout', now(), now())",
        id(), applicationId.value());
    var attributeId = id();
    insert(
        "insert into application_attributes (id, application_id, name, first_seen_at, last_seen_at)"
            + " values (?, ?, 'plano', now(), now())",
        attributeId, applicationId.value());
    insert(
        "insert into application_attribute_values (id, attribute_id, value, last_seen_at)"
            + " values (?, ?, 'pro', now())",
        id(), attributeId);
    insert(
        "insert into sdk_version_usage (id, application_id, version, request_count, first_seen_at,"
            + " last_seen_at) values (?, ?, '1.0.0', 3, now(), now())",
        id(), applicationId.value());
    insert(
        "insert into sdk_version_daily_usage (application_id, version, day, request_count)"
            + " values (?, '1.0.0', current_date, 3)",
        applicationId.value());
    insert(
        "insert into sdk_error_reports (id, application_id, kind, message, occurred_at, received_at)"
            + " values (?, ?, 'UNKNOWN', 'falhou', now(), now())",
        id(), applicationId.value());
    insert(
        "insert into deletion_audits (id, application_id, displays_deleted, answers_deleted,"
            + " performed_at) values (?, ?, 1, 1, now())",
        id(), applicationId.value());
    insert(
        "insert into retention_runs (id, application_id, answers_deleted, texts_cleared, ran_at)"
            + " values (?, ?, 1, 0, now())",
        id(), applicationId.value());
    var snapshotId = id();
    insert(
        "insert into aggregate_snapshots (id, survey_id, version_id, reason, discarded_before,"
            + " responding_displays, computed_at) values (?, ?, ?, 'RETENTION', now(), 1, now())",
        snapshotId, surveyId.value(), versionId.value());
    insert(
        "insert into aggregate_snapshot_counts (snapshot_id, question_key, dimension, value, count)"
            + " values (?, ?, 'STATUS', 'ANSWERED', 1)",
        snapshotId, choice.value());
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private void insert(String sql, Object... params) {
    var statement = jdbc.sql(sql);
    for (var param : params) {
      statement = statement.param(param);
    }
    statement.update();
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  @DisplayName("Apagar o respondente leva exibições, respostas, opções, atributos e supressões")
  void respondente() {
    jdbc.sql("delete from respondents where id = ?").param(respondentId.value()).update();

    assertThat(count("survey_displays")).isZero();
    assertThat(count("survey_answers")).isZero();
    assertThat(count("survey_answer_options")).isZero();
    assertThat(count("survey_display_attributes")).isZero();
    assertThat(count("suppression_events")).isZero();
    assertThat(count("surveys")).isOne();
    assertThat(count("aggregate_snapshots")).isOne();
  }

  @Test
  @DisplayName("Apagar a pesquisa leva versões, perguntas, exibições, respostas e o congelado")
  void pesquisa() {
    jdbc.sql("delete from surveys where id = ?").param(surveyId.value()).update();

    assertThat(count("survey_versions")).isZero();
    assertThat(count("questions")).isZero();
    assertThat(count("question_options")).isZero();
    assertThat(count("survey_displays")).isZero();
    assertThat(count("survey_answers")).isZero();
    assertThat(count("suppression_events")).isZero();
    assertThat(count("aggregate_snapshots")).isZero();
    assertThat(count("aggregate_snapshot_counts")).isZero();
    assertThat(count("respondents")).isOne();
  }

  @Test
  @DisplayName("Apagar a aplicação não deixa nenhuma linha dela em tabela nenhuma")
  void aplicacao() {
    jdbc.sql("delete from applications where id = ?").param(applicationId.value()).update();

    APPLICATION_TABLES.forEach(table -> assertThat(count(table)).as(table).isZero());
  }
}
