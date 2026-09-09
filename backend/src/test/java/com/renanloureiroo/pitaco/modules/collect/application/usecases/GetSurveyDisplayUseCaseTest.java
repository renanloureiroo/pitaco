package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadOutput;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryAnswerRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyDisplayUseCase")
class GetSurveyDisplayUseCaseTest {

  private static final Instant OPENED_AT = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant LONG_AGO = Instant.parse("2020-01-01T10:00:00Z");

  private InMemoryCollectApplicationScopeGateway applications;
  private InMemoryPublishedSurveyCatalog catalog;
  private InMemorySurveyDisplayRepository displays;
  private InMemoryAnswerRepository answers;
  private GetSurveyDisplayUseCase useCase;

  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;
  private RespondentId respondentId;
  private QuestionKey text;
  private QuestionKey choice;
  private QuestionKey number;
  private QuestionKey optional;

  @BeforeEach
  void setUp() {
    applications = new InMemoryCollectApplicationScopeGateway();
    catalog = new InMemoryPublishedSurveyCatalog();
    displays = new InMemorySurveyDisplayRepository();
    answers = new InMemoryAnswerRepository();
    useCase = new GetSurveyDisplayUseCase(applications, catalog, displays, answers);

    applicationId = applications.anActiveApplication();
    surveyId = SurveyId.generate();
    versionId = SurveyVersionId.generate();
    respondentId = RespondentId.generate();
    text = QuestionKey.generate();
    choice = QuestionKey.generate();
    number = QuestionKey.generate();
    optional = QuestionKey.generate();

    catalog.withContent(content(text, choice, number, optional));
  }

  private PublishedSurveyCatalog.DeliverableSurvey content(QuestionKey... keys) {
    var questions = new java.util.ArrayList<PublishedSurveyCatalog.DeliverableQuestion>();
    for (var position = 0; position < keys.length; position++) {
      questions.add(
          new PublishedSurveyCatalog.DeliverableQuestion(
              keys[position],
              position + 1,
              "Pergunta " + (position + 1),
              com.renanloureiroo.pitaco.core.catalog.QuestionType.FREE_TEXT,
              false,
              List.of(),
              java.util.Optional.empty()));
    }

    return new PublishedSurveyCatalog.DeliverableSurvey(surveyId, versionId, 3, questions);
  }

  private DisplayId anOpenDisplay() {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(versionId)
        .forRespondent(respondentId)
        .openedAt(OPENED_AT)
        .withAttributes(Map.of("plan", "pro"))
        .buildSavedIn(displays)
        .id();
  }

  private DisplayId aCompletedDisplay() {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(versionId)
        .forRespondent(respondentId)
        .openedAt(OPENED_AT)
        .withAttributes(Map.of("plan", "pro", "locale", "pt-BR"))
        .completedAt(OPENED_AT.plusSeconds(60))
        .buildSavedIn(displays)
        .id();
  }

  private GetSurveyDisplayUseCase.Input input(DisplayId displayId) {
    return new GetSurveyDisplayUseCase.Input(applicationId.value(), displayId.value());
  }

  @Test
  @DisplayName("Distingue respondida, pulada e expirada na mesma exibição")
  void distingue_as_tres_situacoes() {
    applications.withOpenTextRetentionDays(applicationId, 30);
    var displayId = aCompletedDisplay();

    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(text)
        .withText("melhorar o relatório")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(choice)
        .withOptions("reports", "alerts")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(number)
        .withNumber(9)
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(optional)
        .skipped()
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);

    var output = useCase.execute(input(displayId));

    assertThat(output.answers())
        .extracting(AnswerReadOutput::status)
        .containsExactly(
            AnswerReadStatus.EXPIRED,
            AnswerReadStatus.ANSWERED,
            AnswerReadStatus.ANSWERED,
            AnswerReadStatus.SKIPPED);

    var expired = output.answers().get(0);
    assertThat(expired.text()).isEmpty();
    assertThat(expired.number()).isEmpty();
    assertThat(expired.options()).isEmpty();

    assertThat(output.answers().get(1).options()).containsExactly("reports", "alerts");
    assertThat(output.answers().get(2).number()).contains(9);
    assertThat(output.answers().get(3).text()).isEmpty();
  }

  @Test
  @DisplayName("Prazo de retenção ausente significa sem expiração, não expiração imediata")
  void sem_prazo_nao_expira() {
    var displayId = aCompletedDisplay();
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(text)
        .withText("resposta antiga")
        .answeredAt(LONG_AGO)
        .buildSavedIn(answers);

    var output = useCase.execute(input(displayId));

    assertThat(output.answers()).singleElement().satisfies(
        answer -> {
          assertThat(answer.status()).isEqualTo(AnswerReadStatus.ANSWERED);
          assertThat(answer.text()).contains("resposta antiga");
        });
  }

  @Test
  @DisplayName("Texto dentro do prazo continua visível")
  void texto_dentro_do_prazo() {
    applications.withOpenTextRetentionDays(applicationId, 3650);
    var displayId = aCompletedDisplay();
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(text)
        .withText("ainda dentro do prazo")
        .answeredAt(Instant.now().minusSeconds(60))
        .buildSavedIn(answers);

    var output = useCase.execute(input(displayId));

    assertThat(output.answers().get(0).status()).isEqualTo(AnswerReadStatus.ANSWERED);
    assertThat(output.answers().get(0).text()).contains("ainda dentro do prazo");
  }

  @Test
  @DisplayName("As respostas saem na ordem das perguntas da versão exibida, não na de gravação")
  void ordena_pela_versao_exibida() {
    var displayId = aCompletedDisplay();
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(number).withNumber(7).buildSavedIn(answers);
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(text).withText("oi").buildSavedIn(answers);
    AnswerFactory.anAnswer()
        .forDisplay(displayId)
        .forQuestion(choice)
        .withOptions("a")
        .buildSavedIn(answers);

    var output = useCase.execute(input(displayId));

    assertThat(output.answers())
        .extracting(AnswerReadOutput::questionKey)
        .containsExactly(text, choice, number);
  }

  @Test
  @DisplayName("Chave que a versão exibida não conhece vai para o fim, com desempate pela chave")
  void chave_desconhecida_vai_para_o_fim() {
    var displayId = aCompletedDisplay();
    var first = QuestionKey.of("11111111-1111-4111-8111-111111111111");
    var second = QuestionKey.of("99999999-9999-4999-8999-999999999999");

    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(second).withNumber(1).buildSavedIn(answers);
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(first).withNumber(2).buildSavedIn(answers);
    AnswerFactory.anAnswer().forDisplay(displayId).forQuestion(choice).withOptions("a").buildSavedIn(answers);

    var output = useCase.execute(input(displayId));

    assertThat(output.answers())
        .extracting(AnswerReadOutput::questionKey)
        .containsExactly(choice, first, second);
  }

  @Test
  @DisplayName("Exibição ainda aberta devolve nenhuma resposta e nenhum fechamento, não erro")
  void exibicao_aberta() {
    var displayId = anOpenDisplay();

    var output = useCase.execute(input(displayId));

    assertThat(output.answers()).isEmpty();
    assertThat(output.summary().outcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(output.summary().closedAt()).isEmpty();
    assertThat(output.summary().versionNumber()).isEqualTo(3);
    assertThat(output.respondentId()).isEqualTo(respondentId);
    assertThat(output.surveyId()).isEqualTo(surveyId);
    assertThat(output.attributes()).containsExactly(Map.entry("plan", "pro"));
  }

  @Test
  @DisplayName("Exibição dispensada devolve o desfecho e nenhuma resposta")
  void exibicao_dispensada() {
    var displayId =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .forRespondent(respondentId)
            .openedAt(OPENED_AT)
            .dismissedAt(OPENED_AT.plusSeconds(5))
            .buildSavedIn(displays)
            .id();

    var output = useCase.execute(input(displayId));

    assertThat(output.summary().outcome()).isEqualTo(DisplayOutcome.DISMISSED);
    assertThat(output.summary().closedAt()).contains(OPENED_AT.plusSeconds(5));
    assertThat(output.answers()).isEmpty();
  }

  @Test
  @DisplayName("Exibição inexistente, de outra aplicação ou malformada recusa igual")
  void recusa_exibicao_fora_do_escopo() {
    var alheia =
        SurveyDisplayFactory.aDisplay()
            .forApplication(ApplicationId.generate())
            .forSurvey(surveyId)
            .forVersion(versionId)
            .buildSavedIn(displays)
            .id();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyDisplayUseCase.Input(
                        applicationId.value(), UUID.randomUUID().toString())))
        .isInstanceOf(DisplayNotFound.class);
    assertThatThrownBy(() -> useCase.execute(input(alheia))).isInstanceOf(DisplayNotFound.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyDisplayUseCase.Input(applicationId.value(), "nao-e-um-id")))
        .isInstanceOf(DisplayNotFound.class);
  }
}
