package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayAlreadyClosed;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SubmissionRejected;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.AnswerDraft;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.RawAnswerValue;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.SubmissionProblem;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerText;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import com.renanloureiroo.pitaco.testsupport.factories.AnswerFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySurveyQuotaGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryAnswerRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SubmitSurveyDisplayUseCase")
class SubmitSurveyDisplayUseCaseTest {

  private static final QuestionKey TEXTO = QuestionKey.generate();
  private static final QuestionKey UNICA = QuestionKey.generate();
  private static final QuestionKey NPS = QuestionKey.generate();

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemoryPublishedSurveyCatalog catalog = new InMemoryPublishedSurveyCatalog();
  private final InMemorySurveyDisplayRepository displays = new InMemorySurveyDisplayRepository();
  private final InMemoryAnswerRepository answers = new InMemoryAnswerRepository();
  private final InMemorySurveyQuotaGateway quotas = new InMemorySurveyQuotaGateway();

  private SubmitSurveyDisplayUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;
  private SurveyVersionId versionId;
  private DisplayId displayId;

  @BeforeEach
  void setUp() {
    useCase = new SubmitSurveyDisplayUseCase(applications, catalog, displays, answers, quotas);
    applicationId = applications.anActiveApplication();
    surveyId = SurveyId.generate();
    versionId = SurveyVersionId.generate();
    catalog.withContent(content(versionId));
    displayId = openDisplay();
  }

  @Test
  @DisplayName("Um conjunto válido grava tudo e conclui a exibição")
  void conjunto_valido_grava_e_conclui() {
    useCase.execute(completion());

    assertThat(answers.findByDisplay(displayId)).hasSize(3);
    assertThat(answers.saveAllCalls())
        .describedAs("uma escrita em lote, nunca uma por item")
        .isEqualTo(1);

    var stored = displays.findById(displayId, applicationId).orElseThrow();
    assertThat(stored.getOutcome()).isEqualTo(DisplayOutcome.COMPLETED);
    assertThat(stored.closedAt()).isPresent();

    var texto =
        answers.findByDisplay(displayId).stream()
            .filter(answer -> answer.getQuestionKey().equals(TEXTO))
            .findFirst()
            .orElseThrow();
    assertThat(texto.value()).contains(new AnswerValue.TextValue(
        AnswerText.of("achei caro")));

    var unica =
        answers.findByDisplay(displayId).stream()
            .filter(answer -> answer.getQuestionKey().equals(UNICA))
            .findFirst()
            .orElseThrow();
    assertThat(unica.value()).contains(new AnswerValue.ChoiceValue(List.of("bom")));
  }

  @Test
  @DisplayName("Envio inválido é recusado e não grava nada (SC-008)")
  void envio_invalido_nao_grava_nada() {
    var invalido =
        new SubmitSurveyDisplayUseCase.Input(
            applicationId.value(),
            displayId.value(),
            DisplayOutcome.COMPLETED,
            List.of(answered(NPS, new RawAnswerValue.RawNumber(11))));

    assertThatThrownBy(() -> useCase.execute(invalido))
        .isInstanceOf(SubmissionRejected.class)
        .satisfies(
            error -> {
              var rejected = (SubmissionRejected) error;
              assertThat(rejected.problems().stream().map(SubmissionProblem::code))
                  .contains(
                      SubmissionProblem.REQUIRED_MISSING, SubmissionProblem.VALUE_OUT_OF_RANGE);
            });

    assertThat(answers.isEmpty()).isTrue();
    assertThat(displays.findById(displayId, applicationId).orElseThrow().getOutcome())
        .isEqualTo(DisplayOutcome.STARTED);
  }

  @Test
  @DisplayName("Exibição inexistente e de outra aplicação dão o mesmo 404")
  void exibicao_inexistente_e_de_outra_aplicacao() {
    var desconhecida =
        new SubmitSurveyDisplayUseCase.Input(
            applicationId.value(),
            UUID.randomUUID().toString(),
            DisplayOutcome.DISMISSED,
            List.of());

    var outraAplicacao = applications.anActiveApplication();
    var deOutraAplicacao =
        new SubmitSurveyDisplayUseCase.Input(
            outraAplicacao.value(), displayId.value(), DisplayOutcome.DISMISSED, List.of());

    assertThatThrownBy(() -> useCase.execute(desconhecida)).isInstanceOf(DisplayNotFound.class);
    assertThatThrownBy(() -> useCase.execute(deOutraAplicacao)).isInstanceOf(DisplayNotFound.class);
  }

  @Test
  @DisplayName("Aplicação inativa recusa a submissão")
  void aplicacao_inativa_recusa() {
    var inactive = applications.anInactiveApplication();
    var display =
        SurveyDisplayFactory.aDisplay()
            .forApplication(inactive)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .buildSavedIn(displays)
            .id();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SubmitSurveyDisplayUseCase.Input(
                        inactive.value(), display.value(), DisplayOutcome.DISMISSED, List.of())))
        .isInstanceOf(ApplicationIsInactive.class);
  }

  @Test
  @DisplayName("A validação usa a versão da exibição, não a publicada corrente")
  void validacao_usa_a_versao_da_exibicao() {
    var novaVersao = SurveyVersionId.generate();
    catalog.withContent(
        new DeliverableSurvey(
            surveyId,
            novaVersao,
            2,
            List.of(
                new DeliverableQuestion(
                    QuestionKey.generate(),
                    1,
                    "Pergunta totalmente diferente",
                    QuestionType.FREE_TEXT,
                    true,
                    List.of(),
                    Optional.empty()))));

    assertThatCode(() -> useCase.execute(completion())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("O envio é aceito mesmo com a pesquisa pausada ou republicada depois da abertura")
  void envio_aceito_apos_mudanca_de_estado() {
    // O catálogo continua devolvendo o conteúdo da versão exibida: publicar de novo ou pausar
    // não a apaga, e é ela que a exibição aponta (FR-038, SC-014).
    assertThatCode(() -> useCase.execute(completion())).doesNotThrowAnyException();
    assertThat(answers.findByDisplay(displayId)).hasSize(3);
  }

  @Nested
  @DisplayName("Reenvio")
  class Reenvio {

    @Test
    @DisplayName("O mesmo pacote de novo não cria segunda resposta (FR-036)")
    void reenvio_identico_e_reconhecido() {
      useCase.execute(completion());

      assertThatCode(() -> useCase.execute(completion())).doesNotThrowAnyException();

      assertThat(answers.findByDisplay(displayId)).hasSize(3);
      assertThat(answers.saveAllCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Desfecho diferente na exibição fechada é conflito")
    void desfecho_diferente_e_conflito() {
      useCase.execute(completion());

      assertThatThrownBy(
              () ->
                  useCase.execute(
                      new SubmitSurveyDisplayUseCase.Input(
                          applicationId.value(),
                          displayId.value(),
                          DisplayOutcome.DISMISSED,
                          List.of())))
          .isInstanceOf(DisplayAlreadyClosed.class);
    }

    @Test
    @DisplayName("Resposta para pergunta ainda não gravada é conflito")
    void resposta_nova_em_exibicao_fechada_e_conflito() {
      useCase.execute(
          new SubmitSurveyDisplayUseCase.Input(
              applicationId.value(),
              displayId.value(),
              DisplayOutcome.DISMISSED,
              List.of(answered(TEXTO, new RawAnswerValue.RawText("achei caro")))));

      assertThatThrownBy(
              () ->
                  useCase.execute(
                      new SubmitSurveyDisplayUseCase.Input(
                          applicationId.value(),
                          displayId.value(),
                          DisplayOutcome.DISMISSED,
                          List.of(answered(NPS, new RawAnswerValue.RawNumber(9))))))
          .isInstanceOf(DisplayAlreadyClosed.class);

      assertThat(answers.findByDisplay(displayId)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Dispensa")
  class Dispensa {

    @Test
    @DisplayName("Dispensar fecha a exibição preservando o que já veio, sem exigir a obrigatória")
    void dispensa_aceita_o_parcial() {
      useCase.execute(
          new SubmitSurveyDisplayUseCase.Input(
              applicationId.value(),
              displayId.value(),
              DisplayOutcome.DISMISSED,
              List.of(answered(TEXTO, new RawAnswerValue.RawText("achei caro")))));

      var stored = displays.findById(displayId, applicationId).orElseThrow();
      assertThat(stored.getOutcome()).isEqualTo(DisplayOutcome.DISMISSED);
      assertThat(stored.closedAt()).isPresent();
      assertThat(answers.findByDisplay(displayId)).hasSize(1);
    }

    @Test
    @DisplayName("Dispensa sem nenhuma resposta ainda guarda a versão exibida")
    void dispensa_sem_resposta() {
      useCase.execute(
          new SubmitSurveyDisplayUseCase.Input(
              applicationId.value(), displayId.value(), DisplayOutcome.DISMISSED, List.of()));

      var stored = displays.findById(displayId, applicationId).orElseThrow();
      assertThat(stored.getOutcome()).isEqualTo(DisplayOutcome.DISMISSED);
      assertThat(stored.getVersionId()).isEqualTo(versionId);
      assertThat(answers.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Exibição dispensada recusa resposta nova")
    void dispensada_recusa_resposta_nova() {
      useCase.execute(
          new SubmitSurveyDisplayUseCase.Input(
              applicationId.value(), displayId.value(), DisplayOutcome.DISMISSED, List.of()));

      assertThatThrownBy(
              () ->
                  useCase.execute(
                      new SubmitSurveyDisplayUseCase.Input(
                          applicationId.value(),
                          displayId.value(),
                          DisplayOutcome.DISMISSED,
                          List.of(answered(TEXTO, new RawAnswerValue.RawText("agora vai"))))))
          .isInstanceOf(DisplayAlreadyClosed.class);
    }

    @Test
    @DisplayName("Exibição concluída não vira dispensada")
    void concluida_nao_vira_dispensada() {
      useCase.execute(completion());

      assertThatThrownBy(
              () ->
                  useCase.execute(
                      new SubmitSurveyDisplayUseCase.Input(
                          applicationId.value(),
                          displayId.value(),
                          DisplayOutcome.DISMISSED,
                          List.of())))
          .isInstanceOf(DisplayAlreadyClosed.class);

      assertThat(displays.findById(displayId, applicationId).orElseThrow().getOutcome())
          .isEqualTo(DisplayOutcome.COMPLETED);
    }
  }

  private SubmitSurveyDisplayUseCase.Input completion() {
    return new SubmitSurveyDisplayUseCase.Input(
        applicationId.value(),
        displayId.value(),
        DisplayOutcome.COMPLETED,
        List.of(
            answered(TEXTO, new RawAnswerValue.RawText("achei caro")),
            answered(UNICA, new RawAnswerValue.RawText("bom")),
            answered(NPS, new RawAnswerValue.RawNumber(9))));
  }

  private DisplayId openDisplay() {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forRespondent(RespondentId.generate())
        .forSurvey(surveyId)
        .forVersion(versionId)
        .buildSavedIn(displays)
        .id();
  }

  private static AnswerDraft answered(QuestionKey key, RawAnswerValue value) {
    return new AnswerDraft(key, AnswerStatus.ANSWERED, Optional.of(value));
  }

  private static DeliverableSurvey content(SurveyVersionId versionId) {
    return new DeliverableSurvey(
        SurveyId.generate(),
        versionId,
        1,
        List.of(
            new DeliverableQuestion(
                TEXTO, 1, "O que achou?", QuestionType.FREE_TEXT, true, List.of(), Optional.empty()),
            new DeliverableQuestion(
                UNICA,
                2,
                "Recomendaria?",
                QuestionType.SINGLE_CHOICE,
                false,
                List.of(new QuestionOption("Bom", "bom", 1), new QuestionOption("Ruim", "ruim", 2)),
                Optional.empty()),
            new DeliverableQuestion(
                NPS, 3, "De 0 a 10?", QuestionType.NPS, false, List.of(), Optional.of(new ScaleRange(0, 10)))));
  }

  @Nested
  @DisplayName("Cota de respostas")
  class Cota {

    private SubmitSurveyDisplayUseCase.Input completionOf(DisplayId display) {
      return new SubmitSurveyDisplayUseCase.Input(
          applicationId.value(),
          display.value(),
          DisplayOutcome.COMPLETED,
          List.of(answered(TEXTO, new RawAnswerValue.RawText("ok"))));
    }

    @Test
    @DisplayName("A conclusão que atinge a cota pede o encerramento da pesquisa")
    void atingir_a_cota_encerra() {
      quotas.withQuota(surveyId, 1);

      useCase.execute(completion());

      assertThat(quotas.endings()).containsExactly(surveyId);
      assertThat(quotas.locks())
          .describedAs("a contagem só acontece com a linha da pesquisa travada")
          .containsExactly(surveyId);
    }

    @Test
    @DisplayName("Abaixo da cota, nada é encerrado")
    void abaixo_da_cota_nao_encerra() {
      quotas.withQuota(surveyId, 2);

      useCase.execute(completion());

      assertThat(quotas.endings()).isEmpty();
    }

    @Test
    @DisplayName("Dispensa não conta para a cota")
    void dispensa_nao_conta() {
      quotas.withQuota(surveyId, 1);

      useCase.execute(
          new SubmitSurveyDisplayUseCase.Input(
              applicationId.value(), displayId.value(), DisplayOutcome.DISMISSED, List.of()));

      assertThat(quotas.endings()).isEmpty();
    }

    @Test
    @DisplayName("Sem cota configurada, nunca encerra")
    void sem_cota_nunca_encerra() {
      useCase.execute(completion());

      assertThat(quotas.endings()).isEmpty();
      assertThat(quotas.locks())
          .describedAs("sem cota, nenhuma conclusão espera pela outra")
          .isEmpty();
    }

    @Test
    @DisplayName("Quem já estava com a pesquisa aberta conclui depois de a cota ser atingida")
    void sessao_aberta_conclui_depois_da_cota() {
      quotas.withQuota(surveyId, 1);
      var aberta = openDisplay();

      useCase.execute(completion());
      useCase.execute(completionOf(aberta));

      assertThat(displays.findById(aberta, applicationId).orElseThrow().getOutcome())
          .isEqualTo(DisplayOutcome.COMPLETED);
      assertThat(displays.countCompleted(surveyId))
          .describedAs("o total passa um pouco da cota, de propósito")
          .isEqualTo(2);
    }
  }
}
