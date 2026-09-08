package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@E2E
@DisplayName("ApiKeyRepositoryJpa — corrida de duas revogações")
class ApiKeyRepositoryJpaRaceTest {

  @Autowired ApiKeyRepository apiKeys;
  @Autowired ApplicationJpaRepository applications;
  @Autowired Transactor transactor;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    database.clean();
    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
  }

  @Test
  @DisplayName("Quem perde a corrida relê o estado que o vencedor gravou, não o que leu antes")
  void quem_perde_a_corrida_rele_do_banco() {
    var apiKey = ApiKeyFactory.anApiKey().forApplication(applicationId).buildSavedIn(apiKeys);

    transactor.runInTransaction(
        () -> {
          var mine = apiKeys.findByIdAndApplicationId(apiKey.id(), applicationId).orElseThrow();
          mine.revoke();

          revokeFromAnotherTransaction(apiKey);

          assertThat(apiKeys.revoke(mine)).isFalse();

          var reread = apiKeys.findByIdAndApplicationId(apiKey.id(), applicationId).orElseThrow();
          assertThat(reread.isRevoked()).isTrue();
        });
  }

  private void revokeFromAnotherTransaction(ApiKey apiKey) {
    CompletableFuture.runAsync(
            () ->
                transactor.runInTransaction(
                    () -> {
                      var theirs =
                          apiKeys
                              .findByIdAndApplicationId(apiKey.id(), applicationId)
                              .orElseThrow();
                      theirs.revoke();
                      assertThat(apiKeys.revoke(theirs)).isTrue();
                    }))
        .join();
  }
}
