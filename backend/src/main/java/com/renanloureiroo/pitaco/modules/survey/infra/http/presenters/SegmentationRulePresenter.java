package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.SegmentationRuleOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SegmentationRuleResponseDTO;
import java.util.List;
import java.util.Locale;

public final class SegmentationRulePresenter {

  private SegmentationRulePresenter() {}

  public static SegmentationRuleResponseDTO present(SegmentationRuleOutput output) {
    return new SegmentationRuleResponseDTO(
        output.id(),
        output.attribute(),
        output.operation().name().toLowerCase(Locale.ROOT),
        output.value().orElse(null));
  }

  public static List<SegmentationRuleResponseDTO> presentAll(List<SegmentationRuleOutput> outputs) {
    return outputs.stream().map(SegmentationRulePresenter::present).toList();
  }
}
