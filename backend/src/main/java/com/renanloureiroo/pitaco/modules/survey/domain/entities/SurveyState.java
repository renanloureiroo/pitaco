package com.renanloureiroo.pitaco.modules.survey.domain.entities;

// Não é persistido: sai de Survey.stateAt, do ciclo de vida somado à janela e ao instante da
// leitura. Uma coluna atualizada por job mentiria entre o limite da janela e a execução dele.
public enum SurveyState {
  DRAFT,
  SCHEDULED,
  ACTIVE,
  PAUSED,
  ENDED
}
