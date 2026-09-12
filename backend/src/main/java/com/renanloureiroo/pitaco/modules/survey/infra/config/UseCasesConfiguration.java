package com.renanloureiroo.pitaco.modules.survey.infra.config;

import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.infra.config.HealthProperties;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.AttributeCatalogGateway;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.SdkTrafficGateway;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.AddQuestionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.AddSegmentationRuleUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.CheckPublicationWarningsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.CheckSurveyPublicationUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.CreateSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DefineTriggerUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DiscardSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DiscardSurveyVersionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.DuplicateSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.EndSurveyByQuotaUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.EndSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetSurveyVersionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetVersionComparabilityUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListStateTransitionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListSurveyVersionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListSurveysUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.OpenSurveyVersionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.PauseSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.PublishSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.RemoveQuestionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.RemoveSegmentationRuleUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ReorderQuestionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ResumeSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.UpdateQuestionUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.UpdateSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.CompletedResponsesGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// O nome do bean é explícito porque modules/app tem uma classe de mesmo nome simples, e o
// scan derivaria o mesmo nome para as duas.
@Configuration(value = "surveyUseCasesConfiguration", proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  CreateSurveyUseCase createSurveyUseCase(
      ApplicationScopeGateway applications,
      SurveyRepository surveys,
      SurveyVersionRepository versions) {
    return new CreateSurveyUseCase(applications, surveys, versions);
  }

  @Bean
  DuplicateSurveyUseCase duplicateSurveyUseCase(
      ApplicationScopeGateway applications,
      SurveyRepository surveys,
      SurveyVersionRepository versions) {
    return new DuplicateSurveyUseCase(applications, surveys, versions);
  }

  @Bean
  ListSurveysUseCase listSurveysUseCase(
      ApplicationScopeGateway applications,
      SurveyRepository surveys,
      SurveyVersionRepository versions) {
    return new ListSurveysUseCase(applications, surveys, versions);
  }

  @Bean
  GetSurveyUseCase getSurveyUseCase(SurveyRepository surveys, SurveyVersionRepository versions) {
    return new GetSurveyUseCase(surveys, versions);
  }

  @Bean
  UpdateSurveyUseCase updateSurveyUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions,
      CompletedResponsesGateway completedResponses) {
    return new UpdateSurveyUseCase(surveys, versions, transitions, completedResponses);
  }

  @Bean
  DiscardSurveyUseCase discardSurveyUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new DiscardSurveyUseCase(surveys, versions);
  }

  @Bean
  AddQuestionUseCase addQuestionUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new AddQuestionUseCase(surveys, versions);
  }

  @Bean
  UpdateQuestionUseCase updateQuestionUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new UpdateQuestionUseCase(surveys, versions);
  }

  @Bean
  RemoveQuestionUseCase removeQuestionUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new RemoveQuestionUseCase(surveys, versions);
  }

  @Bean
  ReorderQuestionsUseCase reorderQuestionsUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new ReorderQuestionsUseCase(surveys, versions);
  }

  @Bean
  DefineTriggerUseCase defineTriggerUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new DefineTriggerUseCase(surveys, versions);
  }

  @Bean
  AddSegmentationRuleUseCase addSegmentationRuleUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new AddSegmentationRuleUseCase(surveys, versions);
  }

  @Bean
  RemoveSegmentationRuleUseCase removeSegmentationRuleUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new RemoveSegmentationRuleUseCase(surveys, versions);
  }

  @Bean
  CheckSurveyPublicationUseCase checkSurveyPublicationUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new CheckSurveyPublicationUseCase(surveys, versions);
  }

  @Bean
  CheckPublicationWarningsUseCase checkPublicationWarningsUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      AttributeCatalogGateway catalog,
      SdkTrafficGateway traffic,
      HealthProperties health) {
    return new CheckPublicationWarningsUseCase(
        surveys, versions, catalog, traffic, health.sdkUsage().recentWindow());
  }

  @Bean
  PublishSurveyUseCase publishSurveyUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions) {
    return new PublishSurveyUseCase(surveys, versions, transitions);
  }

  @Bean
  GetSurveyVersionUseCase getSurveyVersionUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new GetSurveyVersionUseCase(surveys, versions);
  }

  @Bean
  PauseSurveyUseCase pauseSurveyUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions) {
    return new PauseSurveyUseCase(surveys, versions, transitions);
  }

  @Bean
  ResumeSurveyUseCase resumeSurveyUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions) {
    return new ResumeSurveyUseCase(surveys, versions, transitions);
  }

  @Bean
  EndSurveyUseCase endSurveyUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions) {
    return new EndSurveyUseCase(surveys, versions, transitions);
  }

  @Bean
  EndSurveyByQuotaUseCase endSurveyByQuotaUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions) {
    return new EndSurveyByQuotaUseCase(surveys, versions, transitions);
  }

  @Bean
  ListStateTransitionsUseCase listStateTransitionsUseCase(
      SurveyRepository surveys,
      SurveyVersionRepository versions,
      SurveyStateTransitionRepository transitions) {
    return new ListStateTransitionsUseCase(surveys, versions, transitions);
  }

  @Bean
  OpenSurveyVersionUseCase openSurveyVersionUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new OpenSurveyVersionUseCase(surveys, versions);
  }

  @Bean
  DiscardSurveyVersionUseCase discardSurveyVersionUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new DiscardSurveyVersionUseCase(surveys, versions);
  }

  @Bean
  ListSurveyVersionsUseCase listSurveyVersionsUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new ListSurveyVersionsUseCase(surveys, versions);
  }

  @Bean
  GetVersionComparabilityUseCase getVersionComparabilityUseCase(
      SurveyRepository surveys, SurveyVersionRepository versions) {
    return new GetVersionComparabilityUseCase(surveys, versions);
  }
}
