package com.renanloureiroo.pitaco.modules.survey.application.gateways;

import com.renanloureiroo.pitaco.core.identity.SurveyId;

// Contar concluídas é trabalho da coleta; a autoria só precisa do número para saber se a cota
// nova já foi atingida.
public interface CompletedResponsesGateway {

  long completedResponsesOf(SurveyId surveyId);
}
