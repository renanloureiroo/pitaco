package com.renanloureiroo.pitaco.modules.survey.domain.entities;

// As quatro primeiras são comandadas e ficam gravadas; as duas últimas são derivadas dos
// limites da janela já passados, na leitura, e nunca viram linha (D-06).
public enum TransitionReason {
  PUBLICATION,
  MANUAL_PAUSE,
  MANUAL_RESUME,
  MANUAL_END,
  WINDOW_OPENED,
  WINDOW_CLOSED
}
