package com.renanloureiroo.pitaco.modules.survey.domain.templates;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import java.util.List;
import java.util.Optional;

// O formato como ele é definido, não uma aproximação. NPS (Reichheld, 2003): 0 a 10, "nada
// provável" a "extremamente provável", a um amigo ou colega. CSAT: 1 a 5, de "muito insatisfeito"
// a "muito satisfeito". CES 2.0 (Gartner/CEB): afirmação de facilidade com concordância de 1 a 7.
// "Este app" no lugar do nome do produto: a pesquisa não sabe como o app se chama para o usuário.
public final class TemplateQuestions {

  static final String NPS_STATEMENT =
      "Em uma escala de 0 a 10, o quanto você recomendaria este app a um amigo ou colega?";
  static final String CSAT_STATEMENT = "O quanto você está satisfeito com este app?";
  static final String CES_STATEMENT = "Este app facilitou resolver o que eu precisava.";

  private TemplateQuestions() {}

  public static List<Question.Draft> draftsFor(SurveyTemplate template) {
    return List.of(
        switch (template) {
          case NPS ->
              draft(
                  NPS_STATEMENT,
                  QuestionType.NPS,
                  ScaleRange.NPS,
                  ScaleLabels.of("Nada provável", "Extremamente provável"));
          case CSAT ->
              draft(
                  CSAT_STATEMENT,
                  QuestionType.RATING,
                  new ScaleRange(1, 5),
                  ScaleLabels.of("Muito insatisfeito", "Muito satisfeito"));
          case CES ->
              draft(
                  CES_STATEMENT,
                  QuestionType.SCALE,
                  new ScaleRange(1, 7),
                  ScaleLabels.of("Discordo totalmente", "Concordo totalmente"));
        });
  }

  private static Question.Draft draft(
      String statement, QuestionType type, ScaleRange range, ScaleLabels labels) {
    return new Question.Draft(
        QuestionStatement.of(statement),
        type,
        true,
        List.of(),
        Optional.of(range),
        labels,
        Optional.empty());
  }
}
