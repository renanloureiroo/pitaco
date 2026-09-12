package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import java.util.List;
import lombok.Getter;

// Catálogo, não perfil: o nome de um atributo que o app já enviou e os valores que ele já
// assumiu, sem nenhuma ligação com quem os enviou. Os valores param de acumular no limite —
// um atributo de alta cardinalidade viraria log, e provavelmente é dado que nem devia chegar.
@Getter
public final class ObservedAttribute extends Entity<ObservedAttributeId> {

  public static final int MAX_VALUES_PER_ATTRIBUTE = 200;

  private static final String INVALID_CODE = "observed_attribute.invalid";

  private final ApplicationId applicationId;
  private final String name;
  private final Instant firstSeenAt;
  private final Instant lastSeenAt;
  private final List<Value> values;

  public record Value(String value, Instant lastSeenAt) {}

  private ObservedAttribute(
      ObservedAttributeId id,
      ApplicationId applicationId,
      String name,
      Instant firstSeenAt,
      Instant lastSeenAt,
      List<Value> values) {
    super(id);

    if (applicationId == null || name == null || name.isBlank()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Atributo observado precisa de aplicação e nome");
    }
    if (firstSeenAt == null || lastSeenAt == null || lastSeenAt.isBefore(firstSeenAt)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Instantes do atributo observado são inválidos");
    }

    this.applicationId = applicationId;
    this.name = name;
    this.firstSeenAt = firstSeenAt;
    this.lastSeenAt = lastSeenAt;
    this.values = List.copyOf(values);
  }

  public static ObservedAttribute restore(
      ObservedAttributeId id,
      ApplicationId applicationId,
      String name,
      Instant firstSeenAt,
      Instant lastSeenAt,
      List<Value> values) {
    return new ObservedAttribute(id, applicationId, name, firstSeenAt, lastSeenAt, values);
  }
}
