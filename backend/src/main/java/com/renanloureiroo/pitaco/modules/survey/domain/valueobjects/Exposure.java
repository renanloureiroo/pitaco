package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.Optional;

// Quanto a pesquisa pode disputar a atenção do respondente: prioridade no desempate, cota que a
// encerra sozinha e isenção do intervalo de descanso da aplicação. Pertence à pesquisa, não à
// versão — mudar a cota não é mudar o instrumento de medida.
public record Exposure(int priority, Optional<Integer> responseQuota, boolean ignoresQuietPeriod) {

  public static final int MIN_PRIORITY = -100;
  public static final int MAX_PRIORITY = 100;
  public static final int DEFAULT_PRIORITY = 0;

  private static final String PRIORITY_INVALID_CODE = "survey.priority_invalid";
  private static final String QUOTA_INVALID_CODE = "survey.response_quota_invalid";

  public Exposure {
    if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
      throw new DomainException(
          ErrorType.VALIDATION,
          PRIORITY_INVALID_CODE,
          "A prioridade deve estar entre " + MIN_PRIORITY + " e " + MAX_PRIORITY);
    }
    if (responseQuota == null) {
      responseQuota = Optional.empty();
    }
    if (responseQuota.filter(quota -> quota < 1).isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION, QUOTA_INVALID_CODE, "A cota de respostas deve ser de ao menos uma");
    }
  }

  public static Exposure standard() {
    return new Exposure(DEFAULT_PRIORITY, Optional.empty(), false);
  }

  public Exposure withPriority(int priority) {
    return new Exposure(priority, responseQuota, ignoresQuietPeriod);
  }

  public Exposure withResponseQuota(int quota) {
    return new Exposure(priority, Optional.of(quota), ignoresQuietPeriod);
  }

  public Exposure withoutResponseQuota() {
    return new Exposure(priority, Optional.empty(), ignoresQuietPeriod);
  }

  public Exposure ignoringQuietPeriod(boolean ignores) {
    return new Exposure(priority, responseQuota, ignores);
  }
}
