package com.renanloureiroo.pitaco.modules.survey.domain.entities;

// O que a coluna guarda: só muda por comando. O estado exposto é derivado daqui com a janela.
public enum SurveyLifecycle {
  DRAFT,
  PUBLISHED,
  PAUSED,
  ENDED
}
