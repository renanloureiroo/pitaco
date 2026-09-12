package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.core.identity.SurveyId;

public record CompetingSurvey(SurveyId surveyId, String name, int priority) {}
