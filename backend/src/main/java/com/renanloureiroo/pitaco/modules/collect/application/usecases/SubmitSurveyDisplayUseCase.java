package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.collect.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayAlreadyClosed;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SubmissionRejected;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyVersionNotDeliverable;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeState;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.AnswerDraft;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.SubmissionValidation;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;

// Um ato atômico: respostas e desfecho chegam juntos, o envio é validado inteiro antes de
// qualquer gravação, e uma recusa deixa zero linhas (D-07, FR-034, SC-008).
@Slf4j
public class SubmitSurveyDisplayUseCase
    implements UseCaseWithoutOutput<SubmitSurveyDisplayUseCase.Input> {

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final SurveyDisplayRepository displays;
  private final AnswerRepository answers;

  public SubmitSurveyDisplayUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      SurveyDisplayRepository displays,
      AnswerRepository answers) {
    this.applications = applications;
    this.catalog = catalog;
    this.displays = displays;
    this.answers = answers;
  }

  public record Input(
      String applicationId, String displayId, DisplayOutcome outcome, List<AnswerDraft> answers) {}

  @Override
  @Transactional
  public void execute(Input input) {
    var applicationId = ApplicationId.of(input.applicationId());

    if (applications.stateOf(applicationId).orElse(null) != ApplicationScopeState.ACTIVE) {
      throw new ApplicationIsInactive();
    }

    var display =
        displays
            .findById(DisplayId.of(input.displayId()), applicationId)
            .orElseThrow(DisplayNotFound::new);

    var stored = answers.findByDisplay(display.id());

    if (display.isClosed()) {
      requireIdenticalReplay(display, input, stored);
      return;
    }

    // A validação usa a versão da exibição, nunca a publicada corrente: pausar, encerrar ou
    // republicar depois da abertura não invalida o que já estava na tela (FR-038, SC-014).
    var questions =
        catalog
            .contentOf(display.getVersionId())
            .orElseThrow(SurveyVersionNotDeliverable::new)
            .questions();

    var problems = SubmissionValidation.check(questions, input.outcome(), input.answers());
    if (!problems.isEmpty()) {
      throw new SubmissionRejected(problems);
    }

    var now = Instant.now();
    answers.saveAll(answersOf(display.id(), questions, input.answers(), now));

    if (input.outcome() == DisplayOutcome.COMPLETED) {
      display.complete(now);
    } else {
      display.dismiss(now);
    }
    displays.update(display);

    // Contagem e desfecho, jamais o conteúdo das respostas (FR-039, SC-013).
    log.info(
        "Exibição encerrada [{}] desfecho={} respostas={}",
        display.id().value(),
        display.getOutcome(),
        input.answers().size());
  }

  // A primeira gravação vence: o mesmo pacote de novo é reconhecido e nada é reescrito; desfecho
  // diferente ou resposta ainda não gravada é conflito (D-08, US3.2, US3.3).
  private static void requireIdenticalReplay(
      SurveyDisplay display, Input input, List<Answer> stored) {
    if (display.getOutcome() != input.outcome()) {
      throw new DisplayAlreadyClosed();
    }

    var storedKeys = stored.stream().map(Answer::getQuestionKey).collect(Collectors.toSet());
    var incoming =
        input.answers().stream().map(AnswerDraft::questionKey).collect(Collectors.toSet());

    if (!storedKeys.containsAll(incoming)) {
      throw new DisplayAlreadyClosed();
    }
  }

  private static List<Answer> answersOf(
      DisplayId displayId, List<DeliverableQuestion> questions, List<AnswerDraft> drafts, Instant now) {

    Map<QuestionKey, DeliverableQuestion> byKey =
        questions.stream()
            .collect(Collectors.toMap(DeliverableQuestion::key, question -> question));

    return drafts.stream()
        .map(
            draft ->
                Answer.create(
                    displayId,
                    draft.questionKey(),
                    draft.status(),
                    draft.asAnswerValue(byKey.get(draft.questionKey()).type()),
                    now))
        .toList();
  }
}
