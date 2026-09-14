package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.DiscardReason;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionDraft;
import com.renanloureiroo.pitaco.modules.collect.domain.interaction.InteractionEvent;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryInteractionEventRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecordInteractionEventsUseCase")
class RecordInteractionEventsUseCaseTest {

  private static final int MAX_PER_DISPLAY = 5;
  private static final Duration WINDOW = Duration.ofDays(7);

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemoryPublishedSurveyCatalog catalog = new InMemoryPublishedSurveyCatalog();
  private final InMemorySurveyDisplayRepository displays = new InMemorySurveyDisplayRepository();
  private final InMemoryInteractionEventRepository events = new InMemoryInteractionEventRepository();

  private final QuestionKey choice = QuestionKey.generate();
  private final QuestionKey text = QuestionKey.generate();
  private final SurveyId surveyId = SurveyId.generate();
  private final SurveyVersionId versionId = SurveyVersionId.generate();

  private RecordInteractionEventsUseCase useCase;
  private ApplicationId applicationId;
  private SurveyDisplay display;

  @BeforeEach
  void setUp() {
    useCase =
        new RecordInteractionEventsUseCase(
            applications, catalog, displays, events, MAX_PER_DISPLAY, WINDOW);
    applicationId = applications.anActiveApplication();
    catalog.withContent(
        new DeliverableSurvey(
            surveyId,
            versionId,
            1,
            List.of(
                new DeliverableQuestion(
                    choice, 1, "Recomendaria?", QuestionType.SINGLE_CHOICE, true,
                    List.of(new QuestionOption("Sim", "yes", 1), new QuestionOption("Não", "no", 2)),
                    Optional.empty()),
                new DeliverableQuestion(
                    text, 2, "O que achou?", QuestionType.FREE_TEXT, false, List.of(), Optional.empty()))));
    display = openedAt(Instant.now().minus(Duration.ofHours(1)));
  }

  private SurveyDisplay openedAt(Instant openedAt) {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(versionId)
        .openedAt(openedAt)
        .buildSavedIn(displays);
  }

  private static InteractionDraft draft(String type, long seq, QuestionKey key, Map<String, Object> data) {
    return new InteractionDraft(
        Optional.of(1),
        Optional.of(type),
        Optional.empty(),
        Optional.of(seq),
        Optional.of(Instant.parse("2026-09-12T13:45:00Z")),
        Optional.of(seq * 100),
        Optional.ofNullable(key).map(QuestionKey::value),
        data);
  }

  private List<InteractionDraft> happyBatch() {
    return List.of(
        draft("survey_presented", 1, null, Map.of("presentation", "bottom-sheet", "questionCount", 2)),
        draft("question_viewed", 2, choice, Map.of("position", 1, "visit", 1, "from", "start")),
        draft("answer_selected", 3, choice, Map.of("value", "yes", "label", "Sim")));
  }

  private RecordInteractionEventsUseCase.Output record(SurveyDisplay target, List<InteractionDraft> drafts) {
    return useCase.execute(
        new RecordInteractionEventsUseCase.Input(applicationId.value(), target.id().value(), drafts));
  }

  @Test
  @DisplayName("Grava o lote legível sob a trava da exibição, com o payload fechado pelo catálogo")
  void grava() {
    var output = record(display, happyBatch());

    assertThat(output.accepted()).isEqualTo(3);
    assertThat(output.duplicated()).isZero();
    assertThat(output.discarded()).isEmpty();
    assertThat(events.findAll()).extracting(InteractionEvent::seq).containsExactly(1, 2, 3);
    assertThat(events.findAll().get(2).data()).containsExactly(Map.entry("value", "yes"));
    assertThat(events.findAll().get(0).data())
        .containsExactly(Map.entry("presentation", "bottom-sheet"), Map.entry("questionCount", 2L));
    assertThat(events.locks()).containsExactly(display.id());
  }

  @Test
  @DisplayName("O mesmo lote reenviado é reconhecido como repetido e não grava de novo")
  void reenvio() {
    record(display, happyBatch());

    var output = record(display, happyBatch());

    assertThat(output.accepted()).isZero();
    assertThat(output.duplicated()).isEqualTo(3);
    assertThat(events.findAll()).hasSize(3);
  }

  @Test
  @DisplayName("Exibição de outra aplicação descarta o lote inteiro sem gravar nem travar")
  void outra_aplicacao() {
    var other = applications.anActiveApplication();
    var foreign =
        SurveyDisplayFactory.aDisplay().forApplication(other).forVersion(versionId).openedAt(Instant.now()).buildSavedIn(displays);

    var output = record(foreign, happyBatch());

    assertThat(output.accepted()).isZero();
    assertThat(output.discarded()).containsExactly(Map.entry(DiscardReason.DISPLAY_UNAVAILABLE, 3));
    assertThat(events.findAll()).isEmpty();
    assertThat(events.locks()).isEmpty();
  }

  @Test
  @DisplayName("Exibição inexistente e identificador malformado dão a mesma resposta da de outra aplicação")
  void inexistente_e_malformada() {
    var missing =
        useCase.execute(
            new RecordInteractionEventsUseCase.Input(applicationId.value(), UUID.randomUUID().toString(), happyBatch()));
    var malformed =
        useCase.execute(new RecordInteractionEventsUseCase.Input(applicationId.value(), "nao-e-uuid", happyBatch()));

    assertThat(missing).isEqualTo(malformed);
    assertThat(missing.discarded()).containsExactly(Map.entry(DiscardReason.DISPLAY_UNAVAILABLE, 3));
    assertThat(events.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Aplicação inativa descarta como exibição indisponível")
  void aplicacao_inativa() {
    applications.withInactive(applicationId);

    var output = record(display, happyBatch());

    assertThat(output.discarded()).containsExactly(Map.entry(DiscardReason.DISPLAY_UNAVAILABLE, 3));
    assertThat(events.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Depois da janela contada da abertura, o lote é descartado; dentro dela, aceito")
  void janela() {
    var late = openedAt(Instant.now().minus(WINDOW).minusSeconds(60));
    var inTime = openedAt(Instant.now().minus(WINDOW).plusSeconds(60));

    assertThat(record(late, happyBatch()).discarded())
        .containsExactly(Map.entry(DiscardReason.OUTSIDE_WINDOW, 3));
    assertThat(record(inTime, happyBatch()).accepted()).isEqualTo(3);
    assertThat(events.findAll()).allSatisfy(event -> assertThat(event.displayId()).isEqualTo(inTime.id()));
  }

  @Test
  @DisplayName("O teto por exibição conta o que já está gravado e corta pela ordem de seq")
  void teto() {
    record(display, List.of(draft("survey_backgrounded", 1, null, Map.of()), draft("survey_foregrounded", 2, null, Map.of())));

    var batch = new ArrayList<InteractionDraft>();
    IntStream.rangeClosed(3, 7).forEach(seq -> batch.add(draft("survey_backgrounded", seq, null, Map.of())));
    var output = record(display, batch);

    assertThat(output.accepted()).isEqualTo(3);
    assertThat(output.discarded()).containsExactly(Map.entry(DiscardReason.OVER_LIMIT, 2));
    assertThat(events.findAll()).extracting(InteractionEvent::seq).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  @DisplayName("Tipo desconhecido, pergunta de fora e escolha em texto livre são contados; o resto grava")
  void descartes_por_evento() {
    var output =
        record(
            display,
            List.of(
                draft("survey_teleported", 1, null, Map.of()),
                draft("question_viewed", 2, QuestionKey.generate(), Map.of()),
                draft("answer_selected", 3, text, Map.of("value", "meu email")),
                draft("text_edited", 4, text, Map.of("length", 9, "text", "meu email")),
                draft("question_viewed", 5, null, Map.of())));

    assertThat(output.accepted()).isOne();
    assertThat(output.discarded())
        .containsOnly(
            Map.entry(DiscardReason.UNKNOWN_TYPE, 1),
            Map.entry(DiscardReason.UNKNOWN_QUESTION, 1),
            Map.entry(DiscardReason.INVALID_ENVELOPE, 2));
    assertThat(events.findAll()).singleElement().satisfies(event -> {
      assertThat(event.type()).isEqualTo(InteractionEventType.TEXT_EDITED);
      assertThat(event.data()).containsExactly(Map.entry("length", 9L));
    });
  }

  @Test
  @DisplayName("Sem nada legível, não trava a exibição nem grava")
  void nada_legivel() {
    var output = record(display, List.of(draft("survey_teleported", 1, null, Map.of())));

    assertThat(output.discarded(DiscardReason.UNKNOWN_TYPE)).isOne();
    assertThat(events.locks()).isEmpty();
    assertThat(events.findAll()).isEmpty();
  }
}
