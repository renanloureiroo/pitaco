package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.publication.PublicationImpediment;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionKey;
import java.util.List;
import java.util.Optional;

public record PublicationImpedimentOutput(String code, String field, Optional<String> questionKey) {

  public static List<PublicationImpedimentOutput> ofAll(List<PublicationImpediment> impediments) {
    return impediments.stream()
        .map(
            impediment ->
                new PublicationImpedimentOutput(
                    impediment.code(),
                    impediment.field(),
                    impediment.questionKey().map(QuestionKey::value)))
        .toList();
  }
}
