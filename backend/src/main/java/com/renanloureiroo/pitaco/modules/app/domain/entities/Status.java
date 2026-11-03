package com.renanloureiroo.pitaco.modules.app.domain.entities;

public enum Status {
  ACTIVE,
  INACTIVE;

  public boolean isActive() {
    return this == ACTIVE;
  }
}
