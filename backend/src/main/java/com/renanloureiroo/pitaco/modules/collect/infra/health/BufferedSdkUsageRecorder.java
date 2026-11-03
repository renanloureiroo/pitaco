package com.renanloureiroo.pitaco.modules.collect.infra.health;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SdkUsageRecorder;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository.UsageDelta;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Contar cada consulta seria uma escrita por evento do app hospedeiro, no caminho que mais
// devolve nada. A contagem acumula em memória e desce ao banco em lote; reiniciar o processo
// perde o que ainda não desceu, e isso é aceito: o número é aproximado por natureza.
@Slf4j
@Component
public class BufferedSdkUsageRecorder implements SdkUsageRecorder {

  // Teto para uma inundação de versões inventadas não crescer a memória sem limite.
  static final int MAX_PENDING_KEYS = 10_000;
  static final int MAX_ATTEMPTS = 3;

  private record Key(ApplicationId applicationId, SdkVersion version, LocalDate day) {}

  // Mutada só dentro do compute do mapa, que serializa o acesso por chave.
  private static final class Tally {
    private long requests;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private int attempts;

    private Tally seen(Instant at) {
      requests++;
      firstSeenAt = firstSeenAt == null || at.isBefore(firstSeenAt) ? at : firstSeenAt;
      lastSeenAt = lastSeenAt == null || at.isAfter(lastSeenAt) ? at : lastSeenAt;
      return this;
    }

    private Tally absorb(Tally other) {
      requests += other.requests;
      firstSeenAt = other.firstSeenAt.isBefore(firstSeenAt) ? other.firstSeenAt : firstSeenAt;
      lastSeenAt = other.lastSeenAt.isAfter(lastSeenAt) ? other.lastSeenAt : lastSeenAt;
      attempts = Math.max(attempts, other.attempts);
      return this;
    }
  }

  private final ConcurrentHashMap<Key, Tally> pending = new ConcurrentHashMap<>();
  private final SdkVersionUsageRepository repository;
  private final Transactor transactor;

  public BufferedSdkUsageRecorder(SdkVersionUsageRepository repository, Transactor transactor) {
    this.repository = repository;
    this.transactor = transactor;
  }

  @Override
  public void record(ApplicationId applicationId, SdkVersion version, Instant now) {
    var key = new Key(applicationId, version, LocalDate.ofInstant(now, ZoneOffset.UTC));

    if (pending.size() >= MAX_PENDING_KEYS && !pending.containsKey(key)) {
      return;
    }

    pending.compute(key, (ignored, tally) -> (tally == null ? new Tally() : tally).seen(now));
  }

  // Uma transação por chave: uma linha que não desce não segura as outras. A que falha volta
  // para o acumulador e tenta de novo na próxima descarga, até desistir.
  @Scheduled(
      fixedDelayString = "${pitaco.health.sdk-usage.flush-interval}",
      initialDelayString = "${pitaco.health.sdk-usage.flush-interval}")
  public void flush() {
    for (var key : pending.keySet()) {
      var tally = pending.remove(key);
      if (tally == null) {
        continue;
      }

      var delta =
          new UsageDelta(
              key.applicationId(),
              key.version(),
              key.day(),
              tally.requests,
              tally.firstSeenAt,
              tally.lastSeenAt);

      try {
        transactor.runInTransaction(() -> repository.increment(delta));
      } catch (RuntimeException failure) {
        retryLater(key, tally, failure);
      }
    }
  }

  @PreDestroy
  void flushOnShutdown() {
    flush();
  }

  int pendingKeys() {
    return pending.size();
  }

  private void retryLater(Key key, Tally tally, RuntimeException failure) {
    tally.attempts++;

    if (tally.attempts >= MAX_ATTEMPTS) {
      log.warn(
          "Uso de versão do SDK descartado application={} version={} consultas={} motivo={}",
          key.applicationId().value(),
          key.version().value(),
          tally.requests,
          failure.getClass().getSimpleName());
      return;
    }

    pending.merge(key, tally, Tally::absorb);
    log.warn(
        "Uso de versão do SDK não gravado, nova tentativa na próxima descarga application={} "
            + "version={} tentativa={} motivo={}",
        key.applicationId().value(),
        key.version().value(),
        tally.attempts,
        failure.getClass().getSimpleName());
  }
}
