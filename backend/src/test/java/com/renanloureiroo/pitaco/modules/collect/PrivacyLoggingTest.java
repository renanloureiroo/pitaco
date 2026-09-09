package com.renanloureiroo.pitaco.modules.collect;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Privacidade no log")
class PrivacyLoggingTest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String REFERENCE = "u-privado-8f1c";
  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";
  private static final String ATTRIBUTE_NAME = "plano_sigiloso";
  private static final String ATTRIBUTE_VALUE = "premium-confidencial";
  private static final String ANSWER_TEXT = "o entregador foi grosseiro comigo";

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;

  private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

  private Logger root;
  private Logger applicationLogger;
  private Level previousLevel;
  private String key;
  private Survey survey;
  private SurveyVersion published;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    ApplicationId applicationId = application.id();

    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();

    survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);

    published =
        SurveyVersionFactory.aVersion()
            .forSurvey(survey.id())
            .withQuestions(QuestionFactory.aFreeTextQuestion().withStatement("O que achou?"))
            .triggeredBy(TriggerFactory.aTrigger().forEvent("checkout.completed").withRate(1.0))
            .buildPublishedSavedIn(versions);

    // O appender fica na raiz para capturar também o que o framework emite; só o logger da
    // aplicação desce a TRACE, de modo que um vazamento em DEBUG apareça sem que a suíte inteira
    // se afogue no TRACE do Hibernate.
    root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    applicationLogger = (Logger) LoggerFactory.getLogger("com.renanloureiroo.pitaco");
    previousLevel = applicationLogger.getLevel();
    applicationLogger.setLevel(Level.TRACE);
    captured.start();
    root.addAppender(captured);
  }

  @AfterEach
  void tearDown() {
    root.detachAppender(captured);
    captured.stop();
    applicationLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("Um ciclo completo não deixa dado do respondente no log (SC-013)")
  void ciclo_completo_nao_vaza_nada() {
    var displayId = UUID.randomUUID().toString();

    eligibility();
    open(displayId);
    submit(displayId);

    assertThat(loggedText())
        .doesNotContain(REFERENCE)
        .doesNotContain(DEVICE)
        .doesNotContain(ATTRIBUTE_NAME)
        .doesNotContain(ATTRIBUTE_VALUE)
        .doesNotContain(ANSWER_TEXT);
  }

  @Test
  @DisplayName("Nem os caminhos de recusa vazam: o log registra campo e motivo, jamais o valor")
  void caminhos_de_recusa_nao_vazam() {
    var displayId = UUID.randomUUID().toString();
    open(displayId);

    // Envio recusado por conteúdo: o texto da resposta não pode aparecer em lugar nenhum.
    client
        .post()
        .uri("/collect/displays/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.COMPLETED,
                List.of(new AnswerDTO(UUID.randomUUID().toString(), AnswerStatus.ANSWERED, ANSWER_TEXT))))
        .exchange()
        .expectStatus()
        .isEqualTo(422);

    // Recusa de constraint: a referência longa demais é entrada do usuário e não vai para o log.
    client
        .post()
        .uri("/collect/eligibility")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new EligibilityRequestDTO(
                "checkout.completed", new RespondentDTO(REFERENCE.repeat(30), null), null))
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(loggedText())
        .doesNotContain(REFERENCE)
        .doesNotContain(DEVICE)
        .doesNotContain(ATTRIBUTE_NAME)
        .doesNotContain(ATTRIBUTE_VALUE)
        .doesNotContain(ANSWER_TEXT);
  }

  // O appender sai antes da leitura: o servidor atende em outra thread e continua logando.
  private String loggedText() {
    root.detachAppender(captured);

    return List.copyOf(captured.list).stream()
        .map(event -> event.getFormattedMessage() + " " + String.join(" ", argumentsOf(event)))
        .reduce("", (all, line) -> all + "\n" + line);
  }

  private static List<String> argumentsOf(ILoggingEvent event) {
    return event.getArgumentArray() == null
        ? List.of()
        : java.util.Arrays.stream(event.getArgumentArray()).map(String::valueOf).toList();
  }

  private void eligibility() {
    client
        .post()
        .uri("/collect/eligibility")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new EligibilityRequestDTO(
                "checkout.completed",
                new RespondentDTO(REFERENCE, DEVICE),
                Map.of(ATTRIBUTE_NAME, ATTRIBUTE_VALUE)))
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void open(String displayId) {
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
                new RespondentDTO(REFERENCE, DEVICE),
                Map.of(ATTRIBUTE_NAME, ATTRIBUTE_VALUE),
                "1.4.2"))
        .exchange()
        .expectStatus()
        .isCreated();
  }

  private void submit(String displayId) {
    client
        .post()
        .uri("/collect/displays/" + displayId + "/submission")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new SubmissionRequestDTO(
                SubmissionRequestDTO.Outcome.COMPLETED,
                List.of(new AnswerDTO(questionKey(), AnswerStatus.ANSWERED, ANSWER_TEXT))))
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  private String questionKey() {
    return published.getQuestions().stream()
        .min(Comparator.comparingInt(Question::getPosition))
        .orElseThrow()
        .getKey()
        .value();
  }
}
