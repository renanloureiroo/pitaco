package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.AnswerDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.OpenDisplayRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SubmissionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Elegibilidade — histórico do respondente")
class CollectEligibilityHistoryE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String EVENT = "checkout.completed";
  private static final String REFERENCE = "u-8f1c";
  private static final Duration DISPLAY_TIMEOUT = Duration.ofMinutes(30);

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired RespondentRepository respondents;
  @Autowired SurveyDisplayRepository displays;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private String key;
  private Survey survey;
  private SurveyVersion published;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();

    survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);

    published = publishVersion(1, 1);
  }

  @Test
  @DisplayName("Quem concluiu não recebe de novo")
  void concluida_nao_volta() {
    resolveWith(SubmissionRequestDTO.Outcome.COMPLETED);

    assertThat(eligibility().survey()).isNull();
  }

  @Test
  @DisplayName("Quem dispensou não recebe de novo")
  void dispensada_nao_volta() {
    resolveWith(SubmissionRequestDTO.Outcome.DISMISSED);

    assertThat(eligibility().survey()).isNull();
  }

  @Test
  @DisplayName("Quem abandonou recebe até o limite, e para nele")
  void abandono_conta_ate_o_limite() {
    var respondent =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference(REFERENCE)
            .buildSavedIn(respondents);

    var vencida = Instant.now().minus(DISPLAY_TIMEOUT).minusSeconds(60);

    abandonedDisplay(respondent.id(), vencida);
    assertThat(eligibility().survey()).isNotNull();

    abandonedDisplay(respondent.id(), vencida.minusSeconds(1));
    assertThat(eligibility().survey()).isNotNull();

    abandonedDisplay(respondent.id(), vencida.minusSeconds(2));
    assertThat(eligibility().survey())
        .describedAs("no terceiro abandono o limite configurado é atingido")
        .isNull();
  }

  @Test
  @DisplayName("Versão semântica nova volta a entregar a quem já havia resolvido")
  void versao_semantica_reabre() {
    resolveWith(SubmissionRequestDTO.Outcome.COMPLETED);
    assertThat(eligibility().survey()).isNull();

    var semantica = republish(2, published.getComparabilityGroup() + 1);

    var entregue = eligibility().survey();
    assertThat(entregue).isNotNull();
    assertThat(entregue.versionId()).isEqualTo(semantica.id().value());
  }

  @Test
  @DisplayName("Versão semântica nova também reabre para quem havia dispensado")
  void versao_semantica_reabre_apos_dispensa() {
    resolveWith(SubmissionRequestDTO.Outcome.DISMISSED);
    assertThat(eligibility().survey()).isNull();

    republish(2, published.getComparabilityGroup() + 1);

    assertThat(eligibility().survey()).isNotNull();
  }

  @Test
  @DisplayName("Versão cosmética não reabre nada: o grupo de comparabilidade é o mesmo")
  void versao_cosmetica_nao_reabre() {
    resolveWith(SubmissionRequestDTO.Outcome.COMPLETED);

    republish(2, published.getComparabilityGroup());

    assertThat(eligibility().survey()).isNull();
  }

  private void resolveWith(SubmissionRequestDTO.Outcome outcome) {
    var displayId = UUID.randomUUID().toString();

    client
        .post()
        .uri("/collect/displays")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new OpenDisplayRequestDTO(
                displayId,
                survey.id().value(),
                published.id().value(),
                new RespondentDTO(REFERENCE, null),
                null,
                null))
        .exchange()
        .expectStatus()
        .isCreated();

    var answers =
        outcome == SubmissionRequestDTO.Outcome.COMPLETED
            ? List.of(new AnswerDTO(questionKeyOf(published), AnswerStatus.ANSWERED, "achei caro"))
            : List.<AnswerDTO>of();

    client
        .post()
        .uri("/collect/displays/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new SubmissionRequestDTO(outcome, answers))
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private void abandonedDisplay(
      com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId respondentId,
      Instant openedAt) {
    SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forRespondent(respondentId)
        .forSurvey(survey.id())
        .forVersion(published.id())
        .inComparabilityGroup(published.getComparabilityGroup())
        .openedAt(openedAt)
        .buildSavedIn(displays);
  }

  private SurveyVersion republish(int number, int comparabilityGroup) {
    var republished = publishVersion(number, comparabilityGroup);

    survey.markPublished(number);
    surveys.update(survey);

    return republished;
  }

  private SurveyVersion publishVersion(int number, int comparabilityGroup) {
    return SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .numbered(number)
        .inComparabilityGroup(comparabilityGroup)
        .withQuestions(QuestionFactory.aFreeTextQuestion().withStatement("O que achou?"))
        .triggeredBy(TriggerFactory.aTrigger().forEvent(EVENT).withRate(1.0))
        .publishedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(number))
        .buildPublishedSavedIn(versions);
  }

  private static String questionKeyOf(SurveyVersion version) {
    return version.getQuestions().stream()
        .min(Comparator.comparingInt(Question::getPosition))
        .orElseThrow()
        .getKey()
        .value();
  }

  private EligibilityResponseDTO eligibility() {
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

    assertThat(body).isNotNull();
    return body;
  }
}
