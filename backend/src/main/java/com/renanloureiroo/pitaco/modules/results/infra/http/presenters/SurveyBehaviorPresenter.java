package com.renanloureiroo.pitaco.modules.results.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.QuestionBehaviorOutput;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.ActiveTimeDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.DismissalsDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.MetricDefinitionDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.QuestionBehaviorDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyBehaviorResponseDTO.ViaCountDTO;
import java.util.Locale;

public final class SurveyBehaviorPresenter {

  private SurveyBehaviorPresenter() {}

  public static SurveyBehaviorResponseDTO present(SurveyBehaviorOutput output) {
    var dismissals = output.dismissals();

    return new SurveyBehaviorResponseDTO(
        output.everPublished(),
        output.displayed(),
        output.instrumented(),
        output.questions().stream().map(SurveyBehaviorPresenter::present).toList(),
        new DismissalsDTO(
            dismissals.total(),
            dismissals.byVia().stream()
                .map(via -> new ViaCountDTO(via.via(), via.count(), via.share().orElse(null)))
                .toList(),
            dismissals.unspecified()),
        SurveyResultsPresenter.present(output.filter()),
        output.definitions().stream()
            .map(definition -> new MetricDefinitionDTO(definition.metric(), definition.definition()))
            .toList());
  }

  private static QuestionBehaviorDTO present(QuestionBehaviorOutput question) {
    var activeTime = question.activeTime();

    return new QuestionBehaviorDTO(
        question.key().value(),
        question.statement(),
        question.type().name().toLowerCase(Locale.ROOT),
        question.position(),
        question.viewed(),
        question.answered(),
        question.skipped(),
        question.abandoned(),
        new ActiveTimeDTO(
            activeTime.samples(),
            activeTime.medianMs().orElse(null),
            activeTime.p90Ms().orElse(null)),
        question.revisited(),
        question.revisitRate().orElse(null),
        question.selected(),
        question.changed(),
        question.answerChangeRate().orElse(null),
        question.validationBlockedDisplays(),
        question.validationBlocks());
  }
}
