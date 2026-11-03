package com.renanloureiroo.pitaco.modules.privacy.domain.retention;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// O prazo geral apaga a resposta; o de texto livre, mais curto, apaga só o texto e deixa a
// resposta contando como dada. Sem nenhum dos dois, nada é descartado: o padrão é não apagar.
public record RetentionPolicy(
    ApplicationId applicationId, Optional<Integer> answerDays, Optional<Integer> textDays) {

  public RetentionPolicy {
    answerDays = answerDays == null ? Optional.empty() : answerDays;
    textDays = textDays == null ? Optional.empty() : textDays;
  }

  // O prazo de texto livre, quando não declarado, é o geral: é o que a aplicação chama de
  // prazo efetivo.
  public static RetentionPolicy of(
      ApplicationId applicationId, Optional<Integer> retentionDays, Optional<Integer> openTextDays) {
    return new RetentionPolicy(applicationId, retentionDays, openTextDays.or(() -> retentionDays));
  }

  public boolean isConfigured() {
    return answerDays.isPresent() || textDays.isPresent();
  }

  public Optional<Instant> answersBefore(Instant reference) {
    return answerDays.map(days -> reference.minus(Duration.ofDays(days)));
  }

  public Optional<Instant> textsBefore(Instant reference) {
    return textDays.map(days -> reference.minus(Duration.ofDays(days)));
  }
}
