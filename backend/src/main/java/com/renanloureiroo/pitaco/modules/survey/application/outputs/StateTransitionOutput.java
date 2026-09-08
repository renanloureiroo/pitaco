package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import java.time.Instant;
import java.util.Optional;

// As transições comandadas vêm da tabela; as da janela são calculadas na leitura e não têm
// linha — por isso o histórico não é uma lista de SurveyStateTransition.
public record StateTransitionOutput(
    SurveyState from,
    SurveyState to,
    TransitionReason reason,
    Optional<String> actor,
    Instant occurredAt) {}
