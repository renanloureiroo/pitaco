package com.renanloureiroo.pitaco.modules.survey.domain.entities;

public enum RuleOperation {
  EQUALS(true),
  NOT_EQUALS(true),
  PRESENT(false),
  ABSENT(false);

  private final boolean requiresValue;

  RuleOperation(boolean requiresValue) {
    this.requiresValue = requiresValue;
  }

  public boolean requiresValue() {
    return requiresValue;
  }
}
