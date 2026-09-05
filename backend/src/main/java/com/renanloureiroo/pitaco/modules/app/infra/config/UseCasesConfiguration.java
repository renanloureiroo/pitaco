package com.renanloureiroo.pitaco.modules.app.infra.config;

import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  CreateApplicationUseCase createApplicationUseCase(ApplicationRepository repository) {
    return new CreateApplicationUseCase(repository);
  }
}
