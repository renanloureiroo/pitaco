package com.renanloureiroo.pitaco.modules.app.infra.config;

import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.application.usecases.AuthenticateApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.GetApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.GetApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.IssueApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApiKeysUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApplicationsUseCase;
import com.renanloureiroo.pitaco.modules.app.application.usecases.RevokeApiKeyUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  AuthenticateApiKeyUseCase authenticateApiKeyUseCase(
      ApiKeyRepository apiKeys, ApplicationRepository applications, Transactor transactor) {
    return new AuthenticateApiKeyUseCase(apiKeys, applications, transactor);
  }

  @Bean
  CreateApplicationUseCase createApplicationUseCase(ApplicationRepository repository) {
    return new CreateApplicationUseCase(repository);
  }

  @Bean
  IssueApiKeyUseCase issueApiKeyUseCase(
      ApplicationRepository applications, ApiKeyRepository apiKeys) {
    return new IssueApiKeyUseCase(applications, apiKeys);
  }

  @Bean
  RevokeApiKeyUseCase revokeApiKeyUseCase(ApiKeyRepository apiKeys) {
    return new RevokeApiKeyUseCase(apiKeys);
  }

  @Bean
  GetApiKeyUseCase getApiKeyUseCase(ApplicationRepository applications, ApiKeyRepository apiKeys) {
    return new GetApiKeyUseCase(applications, apiKeys);
  }

  @Bean
  GetApplicationUseCase getApplicationUseCase(ApplicationRepository applications) {
    return new GetApplicationUseCase(applications);
  }

  @Bean
  ListApplicationsUseCase listApplicationsUseCase(ApplicationRepository applications) {
    return new ListApplicationsUseCase(applications);
  }

  @Bean
  ListApiKeysUseCase listApiKeysUseCase(
      ApplicationRepository applications, ApiKeyRepository apiKeys) {
    return new ListApiKeysUseCase(applications, apiKeys);
  }
}
