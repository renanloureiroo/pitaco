package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyInvalid;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import java.time.Duration;
import java.time.Instant;

// Sem @Transactional na classe: autenticar é leitura, e o caminho quente não deve abrir
// transação por requisição. A única escrita possível — o registro do último uso, amortizado em
// uma por chave por minuto — abre a sua ali onde acontece (D-17, D-18).
public class AuthenticateApiKeyUseCase
    implements UseCase<AuthenticateApiKeyUseCase.Input, AuthenticateApiKeyUseCase.Output> {

  static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

  private final ApiKeyRepository apiKeys;
  private final ApplicationRepository applications;
  private final Transactor transactor;

  public AuthenticateApiKeyUseCase(
      ApiKeyRepository apiKeys, ApplicationRepository applications, Transactor transactor) {
    this.apiKeys = apiKeys;
    this.applications = applications;
    this.transactor = transactor;
  }

  public record Input(String presentedKey) {}

  public record Output(String applicationId, boolean applicationActive) {}

  @Override
  public Output execute(Input input) {
    var apiKey =
        apiKeys
            .findActiveBySecretHash(ApiKeySecret.hashOf(input.presentedKey()))
            .orElseThrow(ApiKeyInvalid::new);

    var now = Instant.now();
    if (apiKey.lastUsedAt().map(last -> last.isBefore(now.minus(TOUCH_INTERVAL))).orElse(true)) {
      transactor.runInTransaction(() -> apiKeys.touch(apiKey.id(), now));
    }

    // A aplicação da chave sumiu: por fora, é indistinguível de chave desconhecida.
    var application =
        applications.findById(apiKey.getApplicationId()).orElseThrow(ApiKeyInvalid::new);

    return new Output(application.id().value(), application.isActive());
  }
}
