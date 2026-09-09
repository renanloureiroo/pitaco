package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@E2E
@DisplayName("Persistência de collect")
class CollectRepositoriesTest {

  private static final Instant OPENED_AT = Instant.parse("2026-09-08T18:00:00Z");

  @Autowired RespondentRepository respondents;
  @Autowired SurveyDisplayRepository displays;
  @Autowired AnswerRepository answers;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    var survey = SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    surveyId = survey.id();
    versionId =
        SurveyVersionFactory.aVersion()
            .forSurvey(surveyId)
            .buildPublishedSavedIn(versions)
            .id();
  }

  @Test
  @DisplayName("Respondente vai e volta inteiro")
  void respondente_ida_e_volta() {
    var respondent =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference("u-8f1c")
            .buildSavedIn(respondents);

    var found = respondents.findByIdentity(applicationId, respondent.getIdentity()).orElseThrow();

    assertThat(found.id()).isEqualTo(respondent.id());
    assertThat(found.getIdentity()).isEqualTo(respondent.getIdentity());
    assertThat(found.getFirstSeenAt()).isEqualTo(respondent.getFirstSeenAt());
    assertThat(found.getLastSeenAt()).isEqualTo(respondent.getLastSeenAt());
  }

  @Test
  @DisplayName("A mesma identidade não entra duas vezes na mesma aplicação")
  void identidade_e_unica_por_aplicacao() {
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("u-8f1c")
        .buildSavedIn(respondents);

    assertThatThrownBy(
            () ->
                RespondentFactory.aRespondent()
                    .forApplication(applicationId)
                    .identifiedByReference("u-8f1c")
                    .buildSavedIn(respondents))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("Exibição vai e volta com o instantâneo de atributos")
  void exibicao_ida_e_volta() {
    var respondent =
        RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);

    var display =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forRespondent(respondent.id())
            .forSurvey(surveyId)
            .forVersion(versionId)
            .withAttributes(Map.of("plano", "premium"))
            .openedAt(OPENED_AT)
            .buildSavedIn(displays);

    var found = displays.findById(display.id(), applicationId).orElseThrow();

    assertThat(found.getOutcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(found.getVersionId()).isEqualTo(versionId);
    assertThat(found.getAttributes().valueOf("plano")).contains("premium");
    assertThat(found.closedAt()).isEmpty();
  }

  @Test
  @DisplayName("findById recorta pela aplicação: exibição de outra nunca é alcançada")
  void exibicao_de_outra_aplicacao_nao_e_alcancada() {
    var respondent =
        RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);
    var display =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forRespondent(respondent.id())
            .forSurvey(surveyId)
            .forVersion(versionId)
            .buildSavedIn(displays);

    assertThat(displays.findById(display.id(), ApplicationId.generate())).isEmpty();
  }

  @Test
  @DisplayName("O histórico do respondente sai em uma consulta para todos os candidatos")
  void historico_do_respondente() {
    var respondent =
        RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);

    SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forRespondent(respondent.id())
        .forSurvey(surveyId)
        .forVersion(versionId)
        .inComparabilityGroup(2)
        .openedAt(OPENED_AT)
        .completedAt(OPENED_AT.plusSeconds(60))
        .buildSavedIn(displays);

    var history = displays.historyOf(respondent.id(), List.of(surveyId));

    assertThat(history).hasSize(1);
    assertThat(history.getFirst().surveyId()).isEqualTo(surveyId);
    assertThat(history.getFirst().comparabilityGroup()).isEqualTo(2);
    assertThat(history.getFirst().outcome()).isEqualTo(DisplayOutcome.COMPLETED);
    assertThat(history.getFirst().openedAt()).isEqualTo(OPENED_AT);
  }

  @Test
  @DisplayName("Resposta vai e volta nas três formas de valor")
  void resposta_ida_e_volta() {
    var display = savedDisplay();

    var text = AnswerFactory.anAnswer().forDisplay(display).withText("achei o frete caro").build();
    var number = AnswerFactory.anAnswer().forDisplay(display).withNumber(9).build();
    var choice = AnswerFactory.anAnswer().forDisplay(display).withOptions("a", "b").build();
    var skipped = AnswerFactory.anAnswer().forDisplay(display).skipped().build();

    answers.saveAll(List.of(text, number, choice, skipped));

    var stored = answers.findByDisplay(display);

    assertThat(stored).hasSize(4);
    assertThat(valueOf(stored, text.getQuestionKey()))
        .isEqualTo(text.value().orElseThrow());
    assertThat(valueOf(stored, number.getQuestionKey()))
        .isEqualTo(new AnswerValue.NumericValue(9));
    assertThat(valueOf(stored, choice.getQuestionKey()))
        .isEqualTo(new AnswerValue.ChoiceValue(List.of("a", "b")));
    assertThat(
            stored.stream()
                .filter(answer -> answer.getQuestionKey().equals(skipped.getQuestionKey()))
                .findFirst()
                .orElseThrow()
                .value())
        .isEmpty();
  }

  @Test
  @DisplayName("Uma resposta por pergunta por exibição, garantido pelo banco")
  void resposta_e_unica_por_pergunta() {
    var display = savedDisplay();
    var questionKey = QuestionKey.generate();

    answers.saveAll(
        List.of(AnswerFactory.anAnswer().forDisplay(display).forQuestion(questionKey).build()));

    assertThatThrownBy(
            () ->
                answers.saveAll(
                    List.of(
                        AnswerFactory.anAnswer()
                            .forDisplay(display)
                            .forQuestion(questionKey)
                            .build())))
        .isInstanceOf(Exception.class);
  }

  private DisplayId savedDisplay() {
    var respondent =
        RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);

    return SurveyDisplayFactory.aDisplay()
        .withId(DisplayId.of(UUID.randomUUID().toString()))
        .forApplication(applicationId)
        .forRespondent(respondent.id())
        .forSurvey(surveyId)
        .forVersion(versionId)
        .buildSavedIn(displays)
        .id();
  }

  private static AnswerValue valueOf(
      List<com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer> stored,
      QuestionKey key) {
    return stored.stream()
        .filter(answer -> answer.getQuestionKey().equals(key))
        .findFirst()
        .orElseThrow()
        .value()
        .orElseThrow();
  }
}
