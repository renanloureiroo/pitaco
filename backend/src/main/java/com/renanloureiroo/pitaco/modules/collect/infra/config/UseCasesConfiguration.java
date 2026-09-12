package com.renanloureiroo.pitaco.modules.collect.infra.config;

import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SdkUsageRecorder;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyQuotaGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedAttributeRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkErrorReportRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SuppressionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.FindEligibleSurveyUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetQuotaProgressUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetSurveyHealthUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkErrorsUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkVersionsUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.PurgeHealthDataUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.RecordSuppressionUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ReportSdkErrorUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListObservedAttributesUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListObservedEventsUseCase;
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
      ObservedEventRepository events,
      ObservedAttributeRepository attributes,
      SdkUsageRecorder sdkUsage,
      Transactor transactor,
      CollectProperties properties) {
    return new FindEligibleSurveyUseCase(
        applications,
        catalog,
        respondents,
        displays,
        events,
        attributes,
        sdkUsage,
        transactor,
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
      AnswerRepository answers,
      SurveyQuotaGateway quotas) {
    return new SubmitSurveyDisplayUseCase(applications, catalog, displays, answers, quotas);
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
  ListObservedEventsUseCase listObservedEventsUseCase(
      ApplicationScopeGateway applications, ObservedEventRepository events) {
    return new ListObservedEventsUseCase(applications, events);
  }

  @Bean
  ListObservedAttributesUseCase listObservedAttributesUseCase(
      ApplicationScopeGateway applications, ObservedAttributeRepository attributes) {
    return new ListObservedAttributesUseCase(applications, attributes);
  }

  @Bean
  GetQuotaProgressUseCase getQuotaProgressUseCase(
      SurveyScopeGateway surveys, SurveyQuotaGateway quotas, SurveyDisplayRepository displays) {
    return new GetQuotaProgressUseCase(surveys, quotas, displays);
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

  @Bean
  RecordSuppressionUseCase recordSuppressionUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      RespondentRepository respondents,
      SuppressionEventRepository suppressions,
      HealthProperties properties) {
    return new RecordSuppressionUseCase(
        applications,
        catalog,
        respondents,
        suppressions,
        properties.suppressions().dedupWindow());
  }

  @Bean
  ReportSdkErrorUseCase reportSdkErrorUseCase(
      ApplicationScopeGateway applications, SdkErrorReportRepository reports) {
    return new ReportSdkErrorUseCase(applications, reports);
  }

  @Bean
  ListSdkVersionsUseCase listSdkVersionsUseCase(
      ApplicationScopeGateway applications,
      SdkVersionUsageRepository usage,
      HealthProperties properties) {
    return new ListSdkVersionsUseCase(
        applications,
        usage,
        properties.sdkUsage().recentWindow(),
        properties.sdkUsage().staleAfter());
  }

  @Bean
  ListSdkErrorsUseCase listSdkErrorsUseCase(
      ApplicationScopeGateway applications, SdkErrorReportRepository reports) {
    return new ListSdkErrorsUseCase(applications, reports);
  }

  @Bean
  GetSurveyHealthUseCase getSurveyHealthUseCase(
      SurveyScopeGateway surveys,
      PublishedSurveyCatalog catalog,
      SuppressionEventRepository suppressions,
      SurveyDisplayRepository displays,
      ObservedEventRepository events,
      HealthProperties properties) {
    var limits = properties.suppressions();
    return new GetSurveyHealthUseCase(
        surveys,
        catalog,
        suppressions,
        displays,
        events,
        limits.defaultWindow(),
        limits.relevantShare(),
        limits.relevantMinimum());
  }

  @Bean
  PurgeHealthDataUseCase purgeHealthDataUseCase(
      SdkErrorReportRepository reports,
      SdkVersionUsageRepository usage,
      HealthProperties properties) {
    return new PurgeHealthDataUseCase(
        reports,
        usage,
        properties.sdkErrors().retention(),
        properties.sdkUsage().dailyRetention());
  }
}
