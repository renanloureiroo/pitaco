package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

// O driver devolve timestamptz como Instant, OffsetDateTime ou Timestamp conforme o caminho da
// consulta nativa; a leitura aceita os três.
final class NativeValues {

  private NativeValues() {}

  static Instant instant(Object value) {
    return switch (value) {
      case null -> null;
      case Instant instant -> instant;
      case OffsetDateTime offset -> offset.toInstant();
      case Timestamp timestamp -> timestamp.toInstant();
      default -> throw new IllegalStateException("Instante inesperado: " + value.getClass());
    };
  }

  static long count(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }
}
