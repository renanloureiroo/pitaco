package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyHealthOutput;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyHealthResponseDTO;

public final class SurveyHealthPresenter {

  private SurveyHealthPresenter() {}

  public static SurveyHealthResponseDTO present(SurveyHealthOutput output) {
    return new SurveyHealthResponseDTO(
        output.from(),
        output.to(),
        output.displays(),
        new SurveyHealthResponseDTO.Suppressions(
            output.suppressions(),
            output.suppressionsByReason().stream()
                .map(count -> new SurveyHealthResponseDTO.ReasonCount(count.reason(), count.count()))
                .toList(),
            output.suppressionsBySdkVersion().stream()
                .map(
                    count ->
                        new SurveyHealthResponseDTO.VersionCount(
                            count.sdkVersion().orElse(null), count.count()))
                .toList()),
        output.suppressionShare().orElse(null),
        output.relevant(),
        output.minRequiredVersion().orElse(null),
        output.eventName().orElse(null),
        output.eventLastSeenAt().orElse(null));
  }
}
