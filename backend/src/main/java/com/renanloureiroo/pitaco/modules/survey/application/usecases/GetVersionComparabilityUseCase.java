package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetVersionComparabilityUseCase
    implements UseCase<
        GetVersionComparabilityUseCase.Input, GetVersionComparabilityUseCase.Output> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public GetVersionComparabilityUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  // O grupo já foi decidido no ato de cada publicação; aqui só se agrupa pela coluna. A
  // transitividade que a spec descreve cai da regra de incremento, não de um cálculo aqui.
  @Override
  public Output execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    Map<Integer, List<Integer>> grouped =
        surveyVersionsRepository.findAllPublished(survey.id()).stream()
            .collect(
                Collectors.groupingBy(
                    SurveyVersion::getComparabilityGroup,
                    TreeMap::new,
                    Collectors.mapping(SurveyVersion::getNumber, Collectors.toList())));

    var groups =
        grouped.entrySet().stream()
            .map(
                entry ->
                    new Group(
                        entry.getKey(),
                        entry.getValue().stream().sorted(Comparator.naturalOrder()).toList()))
            .toList();

    log.info("Comparabilidade consultada survey={} grupos={}", survey.id().value(), groups.size());

    return new Output(groups);
  }

  public record Input(String applicationId, String surveyId) {}

  public record Output(List<Group> groups) {}

  public record Group(int group, List<Integer> versions) {}
}
