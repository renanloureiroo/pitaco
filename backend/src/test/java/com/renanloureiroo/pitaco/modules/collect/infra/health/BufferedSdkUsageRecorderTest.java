package com.renanloureiroo.pitaco.modules.collect.infra.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySdkVersionUsageRepository;
import com.renanloureiroo.pitaco.testsupport.transaction.DirectTransactor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BufferedSdkUsageRecorder")
class BufferedSdkUsageRecorderTest {

  private static final Instant MORNING = Instant.parse("2026-09-12T09:00:00Z");
  private static final Instant EVENING = Instant.parse("2026-09-12T21:00:00Z");
  private static final Instant NEXT_DAY = Instant.parse("2026-09-13T01:00:00Z");

  private final InMemorySdkVersionUsageRepository repository =
      new InMemorySdkVersionUsageRepository();
  private final BufferedSdkUsageRecorder recorder =
      new BufferedSdkUsageRecorder(repository, new DirectTransactor());
  private final ApplicationId application = ApplicationId.generate();
  private final SdkVersion version = SdkVersion.of("1.0.0");

  @Test
  @DisplayName("Acumula em memória e desce um delta por aplicação, versão e dia")
  void acumula_e_desce() {
    recorder.record(application, version, EVENING);
    recorder.record(application, version, MORNING);
    recorder.record(application, version, NEXT_DAY);

    assertThat(repository.increments()).isEmpty();

    recorder.flush();

    assertThat(repository.increments())
        .hasSize(2)
        .anySatisfy(
            delta -> {
              assertThat(delta.day()).isEqualTo(LocalDate.parse("2026-09-12"));
              assertThat(delta.requests()).isEqualTo(2);
              assertThat(delta.firstSeenAt()).isEqualTo(MORNING);
              assertThat(delta.lastSeenAt()).isEqualTo(EVENING);
            });
    assertThat(recorder.pendingKeys()).isZero();
  }

  @Test
  @DisplayName("Descarga que falha volta ao acumulador e desce na seguinte, sem perder contagem")
  void falha_tenta_de_novo() {
    repository.failingTimes(1);
    recorder.record(application, version, MORNING);

    recorder.flush();
    assertThat(repository.increments()).isEmpty();
    assertThat(recorder.pendingKeys()).isEqualTo(1);

    recorder.record(application, version, EVENING);
    recorder.flush();

    assertThat(repository.increments())
        .singleElement()
        .satisfies(delta -> assertThat(delta.requests()).isEqualTo(2));
  }

  @Test
  @DisplayName("Depois do limite de tentativas, o delta é descartado")
  void desiste_depois_do_limite() {
    repository.failingTimes(BufferedSdkUsageRecorder.MAX_ATTEMPTS);
    recorder.record(application, version, MORNING);

    for (var attempt = 0; attempt < BufferedSdkUsageRecorder.MAX_ATTEMPTS; attempt++) {
      recorder.flush();
    }

    assertThat(recorder.pendingKeys()).isZero();
    assertThat(repository.increments()).isEmpty();
  }

  @Test
  @DisplayName("Registro concorrente não perde contagem")
  void concorrencia() throws InterruptedException {
    var executor = Executors.newFixedThreadPool(8);
    for (var index = 0; index < 4000; index++) {
      executor.submit(() -> recorder.record(application, version, MORNING));
    }
    executor.shutdown();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    recorder.flush();

    assertThat(repository.increments())
        .singleElement()
        .satisfies(delta -> assertThat(delta.requests()).isEqualTo(4000));
  }
}
