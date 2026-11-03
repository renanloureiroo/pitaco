package com.renanloureiroo.pitaco.infra.http.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Janela fixa em memória, sem Redis: um processo só atende a API, e a precisão de um token
// bucket não compra nada aqui. O expurgo é oportunista, no próprio caminho de decisão, para
// que chaves e origens que sumiram não fiquem residentes para sempre.
public final class FixedWindowRateLimiter {

  private final int capacity;
  private final long windowMillis;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong nextPurgeAt;

  public FixedWindowRateLimiter(int capacity, Duration window, Clock clock) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity deve ser ao menos 1");
    }
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window deve ser positiva");
    }
    this.capacity = capacity;
    this.windowMillis = window.toMillis();
    this.clock = clock;
    this.nextPurgeAt = new AtomicLong(clock.millis() + windowMillis);
  }

  public FixedWindowRateLimiter(int capacity, Duration window) {
    this(capacity, window, Clock.systemUTC());
  }

  public record Decision(boolean allowed, long retryAfterSeconds) {}

  public Decision tryAcquire(String subject) {
    var now = clock.millis();
    purgeIfDue(now);

    var window =
        windows.compute(
            subject,
            (key, current) ->
                current == null || current.expired(now) ? new Window(now + windowMillis) : current);
    if (window.count.incrementAndGet() <= capacity) {
      return new Decision(true, 0);
    }

    var remainingMillis = Math.max(1, window.endsAt - now);
    return new Decision(false, (remainingMillis + 999) / 1000);
  }

  public int trackedSubjects() {
    return windows.size();
  }

  public boolean tracks(String subject) {
    return windows.containsKey(subject);
  }

  private void purgeIfDue(long now) {
    var due = nextPurgeAt.get();
    if (now < due || !nextPurgeAt.compareAndSet(due, now + windowMillis)) {
      return;
    }
    windows.entrySet().removeIf(entry -> entry.getValue().expired(now));
  }

  private static final class Window {
    private final long endsAt;
    private final AtomicInteger count = new AtomicInteger();

    private Window(long endsAt) {
      this.endsAt = endsAt;
    }

    private boolean expired(long now) {
      return now >= endsAt;
    }
  }
}
