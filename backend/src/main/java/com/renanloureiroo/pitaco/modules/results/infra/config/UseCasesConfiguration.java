package com.renanloureiroo.pitaco.modules.results.infra.config;

import com.renanloureiroo.pitaco.modules.collect.infra.config.CollectProperties;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ExportSurveyResultsUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.GetSurveyResultsUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ListOpenAnswersUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// O prazo de abandono é o mesmo da coleta, lido da mesma propriedade: dois números para a
// mesma regra divergiriam na primeira mudança.
@Configuration(value = "resultsUseCasesConfiguration", proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  GetSurveyResultsUseCase getSurveyResultsUseCase(
      SurveyScopeGateway surveys,
      SurveyResultsReadModel results,
      CollectProperties collect,
      ResultsProperties properties) {
    return new GetSurveyResultsUseCase(
        surveys, results, collect.displayTimeout(), properties.smallSampleThreshold());
  }

  @Bean
  ListOpenAnswersUseCase listOpenAnswersUseCase(
      SurveyScopeGateway surveys, SurveyResultsReadModel results) {
    return new ListOpenAnswersUseCase(surveys, results);
  }

  @Bean
  ExportSurveyResultsUseCase exportSurveyResultsUseCase(
      SurveyScopeGateway surveys, SurveyResultsReadModel results, CollectProperties collect) {
    return new ExportSurveyResultsUseCase(surveys, results, collect.displayTimeout());
  }
}
