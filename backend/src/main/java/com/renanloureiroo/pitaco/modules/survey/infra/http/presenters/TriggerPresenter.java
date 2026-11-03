package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.TriggerOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.TriggerResponseDTO;

public final class TriggerPresenter {

  private TriggerPresenter() {}

  public static TriggerResponseDTO present(TriggerOutput output) {
    return new TriggerResponseDTO(
        output.eventName(),
        output.windowStart(),
        output.windowEnd().orElse(null),
        output.samplingRate(),
        SegmentationRulePresenter.presentAll(output.rules()));
  }
}
