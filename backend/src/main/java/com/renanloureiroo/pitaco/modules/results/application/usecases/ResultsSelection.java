package com.renanloureiroo.pitaco.modules.results.application.usecases;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ResultsFilterOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AttributeFilter;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.ResultsFilter;
import java.time.Instant;
import java.util.Optional;

// O mesmo recorte para as três leituras, para que agregado, taxa, respostas abertas e export
// contem sempre o mesmo conjunto. Atributo sem valor é o recorte "sem o atributo".
public record ResultsSelection(
    Optional<Instant> from,
    Optional<Instant> to,
    Optional<String> attribute,
    Optional<String> attributeValue,
    Optional<Integer> versionNumber) {

  public static ResultsSelection none() {
    return new ResultsSelection(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public ResultsFilter toFilter(ApplicationId applicationId, SurveyId surveyId) {
    return new ResultsFilter(
        applicationId,
        surveyId,
        from,
        to,
        attribute.map(name -> new AttributeFilter(name, attributeValue)),
        versionNumber);
  }

  // Sem período nem atributo: o único recorte que o agregado congelado pela retenção honra.
  public boolean isVersionOnly() {
    return from.isEmpty() && to.isEmpty() && attribute.isEmpty();
  }

  public ResultsFilterOutput toOutput() {
    return new ResultsFilterOutput(
        from,
        to,
        attribute,
        attributeValue,
        attribute.isPresent() && attributeValue.isEmpty(),
        versionNumber);
  }
}
