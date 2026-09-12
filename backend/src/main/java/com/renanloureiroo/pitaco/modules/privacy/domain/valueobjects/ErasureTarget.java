package com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.text.RequiredText;
import java.util.Optional;

// Quem apagar, pela mesma identidade opaca com que o SDK o apresentou: a referência do app ou,
// para quem nunca teve uma, o dispositivo. Exatamente uma das duas; o valor nunca vai para log.
public record ErasureTarget(Kind kind, String value) {

  public static final int MAX_VALUE_LENGTH = 200;

  private static final String REQUIRED_CODE = "respondent.identity_required";
  private static final String AMBIGUOUS_CODE = "respondent.identity_ambiguous";
  private static final String INVALID_CODE = "respondent.identity_invalid";

  public enum Kind {
    APP_REFERENCE,
    DEVICE
  }

  public ErasureTarget {
    if (kind == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Tipo de identificação é obrigatório");
    }
    value = RequiredText.of(value, MAX_VALUE_LENGTH, INVALID_CODE, "Identificação do respondente");
  }

  public static ErasureTarget of(Optional<String> reference, Optional<String> deviceId) {
    var appReference = present(reference);
    var device = present(deviceId);

    if (appReference.isPresent() && device.isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          AMBIGUOUS_CODE,
          "Informe a referência do app ou o identificador do dispositivo, não os dois");
    }

    return appReference
        .map(text -> new ErasureTarget(Kind.APP_REFERENCE, text))
        .or(() -> device.map(text -> new ErasureTarget(Kind.DEVICE, text)))
        .orElseThrow(
            () ->
                new DomainException(
                    ErrorType.VALIDATION,
                    REQUIRED_CODE,
                    "É preciso informar a referência do app ou o identificador do dispositivo"));
  }

  private static Optional<String> present(Optional<String> candidate) {
    return candidate == null
        ? Optional.empty()
        : candidate.map(String::strip).filter(text -> !text.isEmpty());
  }
}
