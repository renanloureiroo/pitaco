package com.renanloureiroo.pitaco.modules.survey.application.services;

import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.Optional;

// O encerramento por cota tem duas portas de entrada — a conclusão que atinge a cota e a cota
// reduzida abaixo do já coletado — e um registro só. O update é condicional no banco: de duas
// transações que chegam juntas, só a que efetivamente mudou o estado grava a transição.
public final class QuotaClosure {

  private QuotaClosure() {}

  public static boolean close(
      Survey survey,
      Optional<TriggerWindow> window,
      Instant now,
      SurveyRepository surveys,
      SurveyStateTransitionRepository transitions) {
    var before = survey.stateAt(now, window);

    // A janela que já fechou encerrou a pesquisa antes da cota: o histórico não ganha um
    // segundo encerramento por um motivo que não foi o dela.
    if (before == SurveyState.ENDED || !survey.endByQuota()) {
      return false;
    }

    if (!surveys.endIfLive(survey.id())) {
      return false;
    }

    transitions.record(
        SurveyStateTransition.record(
            survey.id(), before, survey.stateAt(now, window), TransitionReason.QUOTA_REACHED, now));

    return true;
  }
}
