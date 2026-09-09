package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.text.RequiredText;
import java.util.Optional;

// Par opaco: nada aqui identifica pessoa, e o valor nunca vai para log (FR-008).
public record RespondentIdentity(RespondentIdentityKind kind, String value) {

  public static final int MAX_VALUE_LENGTH = 200;

  private static final String REQUIRED_CODE = "respondent.identity_required";
  private static final String INVALID_CODE = "respondent.identity_invalid";

  public RespondentIdentity {
    if (kind == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Tipo de identificação é obrigatório");
    }
    value = RequiredText.of(value, MAX_VALUE_LENGTH, INVALID_CODE, "Identificação do respondente");
  }

  public static RespondentIdentity of(
      Optional<String> appReference, Optional<String> deviceId) {
    return present(appReference)
        .map(reference -> new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, reference))
        .or(
            () ->
                present(deviceId)
                    .map(device -> new RespondentIdentity(RespondentIdentityKind.DEVICE, device)))
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
