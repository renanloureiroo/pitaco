package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.PublicationImpedimentOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationImpedimentDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationImpedimentsResponseDTO;
import java.util.List;

public final class PublicationImpedimentsPresenter {

  private PublicationImpedimentsPresenter() {}

  public static PublicationImpedimentsResponseDTO present(
      List<PublicationImpedimentOutput> impediments) {
    return new PublicationImpedimentsResponseDTO(
        impediments.stream()
            .map(
                impediment ->
                    new PublicationImpedimentDTO(
                        impediment.code(),
                        impediment.field(),
                        impediment.questionKey().orElse(null)))
            .toList());
  }
}
