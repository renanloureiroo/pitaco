package com.renanloureiroo.pitaco.modules.collect.infra.config;

import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.FindEligibleSurveyUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentsUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSurveyDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.OpenSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.SubmitSurveyDisplayUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// O nome do bean é explícito porque `app` e `survey` têm classes de mesmo nome simples, e o scan
// derivaria o mesmo nome para as três.
@Configuration(value = "collectUseCasesConfiguration", proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  FindEligibleSurveyUseCase findEligibleSurveyUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SurveyDisplayRepository displays,
      CollectProperties properties) {
    return new FindEligibleSurveyUseCase(
        applications,
        catalog,
        respondents,
        displays,
        properties.displayTimeout(),
        properties.maxAttempts());
  }

  @Bean
  OpenSurveyDisplayUseCase openSurveyDisplayUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SurveyDisplayRepository displays) {
    return new OpenSurveyDisplayUseCase(applications, catalog, respondents, displays);
  }

  @Bean
  SubmitSurveyDisplayUseCase submitSurveyDisplayUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      SurveyDisplayRepository displays,
      AnswerRepository answers) {
    return new SubmitSurveyDisplayUseCase(applications, catalog, displays, answers);
  }

  @Bean
  GetSurveyDisplayUseCase getSurveyDisplayUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      SurveyDisplayRepository displays,
      AnswerRepository answers) {
    return new GetSurveyDisplayUseCase(applications, catalog, displays, answers);
  }

  @Bean
  ListRespondentsUseCase listRespondentsUseCase(
      ApplicationScopeGateway applications, RespondentRepository respondents) {
    return new ListRespondentsUseCase(applications, respondents);
  }

  @Bean
  ListSurveyDisplaysUseCase listSurveyDisplaysUseCase(
      SurveyScopeGateway surveys, SurveyDisplayRepository displays) {
    return new ListSurveyDisplaysUseCase(surveys, displays);
  }

  @Bean
  ListRespondentDisplaysUseCase listRespondentDisplaysUseCase(
      RespondentRepository respondents, SurveyDisplayRepository displays) {
    return new ListRespondentDisplaysUseCase(respondents, displays);
  }
}
