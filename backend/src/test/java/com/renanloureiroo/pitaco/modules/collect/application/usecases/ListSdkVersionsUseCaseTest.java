package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SdkVersionUsageOutput;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySdkVersionUsageRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListSdkVersionsUseCase")
class ListSdkVersionsUseCaseTest {

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemorySdkVersionUsageRepository usage = new InMemorySdkVersionUsageRepository();

  private ListSdkVersionsUseCase useCase;
  private ApplicationId applicationId;
  private Instant now;
  private LocalDate today;

  @BeforeEach
  void setUp() {
    useCase =
        new ListSdkVersionsUseCase(applications, usage, Duration.ofDays(14), Duration.ofDays(14));
    applicationId = applications.anActiveApplication();
    now = Instant.now();
    today = LocalDate.ofInstant(now, ZoneOffset.UTC);
  }

  @Test
  @DisplayName("Proporção do tráfego recente, versões da mais nova para a mais antiga")
  void proporcao_recente() {
    usage
        .withUsage(applicationId, "1.0.0", today, 30, now.minus(Duration.ofDays(60)), now)
        .withUsage(applicationId, "1.1.0", today, 90, now.minus(Duration.ofDays(3)), now)
        .withUsage(applicationId, "1.0.0", today.minusDays(40), 5000, now, now);

    var output = useCase.execute(new ListSdkVersionsUseCase.Input(applicationId.value()));

    assertThat(output.recentRequests()).isEqualTo(120);
    assertThat(output.versions())
        .extracting(SdkVersionUsageOutput::version)
        .containsExactly("1.1.0", "1.0.0");

    var old = output.versions().getLast();
    assertThat(old.requestCount()).isEqualTo(5030);
    assertThat(old.recentRequestCount()).isEqualTo(30);
    assertThat(old.recentShare().orElseThrow()).isCloseTo(0.25, within(1e-9));
  }

  @Test
  @DisplayName("Versão sem contato além do limite aparece como sumida do tráfego")
  void versao_sumida() {
    var longAgo = now.minus(Duration.ofDays(30));
    usage
        .withUsage(applicationId, "0.9.0", today.minusDays(30), 10, longAgo, longAgo)
        .withUsage(applicationId, "1.0.0", today, 10, now, now);

    var output = useCase.execute(new ListSdkVersionsUseCase.Input(applicationId.value()));

    assertThat(output.versions())
        .filteredOn(version -> version.version().equals("0.9.0"))
        .singleElement()
        .satisfies(
            version -> {
              assertThat(version.stale()).isTrue();
              assertThat(version.recentRequestCount()).isZero();
              assertThat(version.recentShare().orElseThrow()).isZero();
            });
    assertThat(output.versions().getFirst().stale()).isFalse();
  }

  @Test
  @DisplayName("Sem consulta recente nenhuma, a proporção fica ausente")
  void sem_trafego_recente() {
    var longAgo = now.minus(Duration.ofDays(30));
    usage.withUsage(applicationId, "1.0.0", today.minusDays(30), 10, longAgo, longAgo);

    var output = useCase.execute(new ListSdkVersionsUseCase.Input(applicationId.value()));

    assertThat(output.recentRequests()).isZero();
    assertThat(output.versions().getFirst().recentShare()).isEmpty();
  }

  @Test
  @DisplayName("Aplicação inexistente é 404")
  void aplicacao_inexistente() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ListSdkVersionsUseCase.Input(ApplicationId.generate().value())))
        .isInstanceOf(ApplicationNotFound.class);
  }
}
