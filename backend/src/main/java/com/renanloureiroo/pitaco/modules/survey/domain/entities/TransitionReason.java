package com.renanloureiroo.pitaco.modules.survey.domain.entities;

// As cinco primeiras ficam gravadas: quatro são comandadas e a da cota é disparada pela
// conclusão que a atinge. As duas últimas são derivadas dos limites da janela já passados, na
// leitura, e nunca viram linha (D-06).
public enum TransitionReason {
  PUBLICATION,
  MANUAL_PAUSE,
  MANUAL_RESUME,
  MANUAL_END,
  QUOTA_REACHED,
  WINDOW_OPENED,
  WINDOW_CLOSED
}
