package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

// De qual formato consagrado a pesquisa partiu. O conteúdo que cada um põe no rascunho mora em
// TemplateQuestions; aqui fica só o que a pesquisa carrega adiante, como o cálculo do NPS.
public enum SurveyTemplate {
  NPS,
  CSAT,
  CES
}
