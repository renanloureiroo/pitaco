package com.renanloureiroo.pitaco.modules.results.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import java.util.Optional;

// Score ausente quando ninguém respondeu: NPS zero seria uma afirmação, e não há dado para ela.
public record NpsSummaryOutput(
    QuestionKey questionKey,
    long respondents,
    long promoters,
    long passives,
    long detractors,
    Optional<Double> score) {}
