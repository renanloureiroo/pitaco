package com.renanloureiroo.pitaco.modules.survey.domain.templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("TemplateQuestions")
class TemplateQuestionsTest {

  @Test
  @DisplayName("NPS: 0 a 10, a um amigo ou colega, de nada provável a extremamente provável")
  void nps() {
    var draft = TemplateQuestions.draftsFor(SurveyTemplate.NPS).getFirst();

    assertThat(draft.type()).isEqualTo(QuestionType.NPS);
    assertThat(draft.range()).contains(ScaleRange.NPS);
    assertThat(draft.statement().value()).contains("0 a 10").contains("amigo ou colega");
    assertThat(draft.labels().min()).contains("Nada provável");
    assertThat(draft.labels().max()).contains("Extremamente provável");
    assertThat(draft.required()).isTrue();
  }

  @Test
  @DisplayName("CSAT: avaliação de 1 a 5, de muito insatisfeito a muito satisfeito")
  void csat() {
    var draft = TemplateQuestions.draftsFor(SurveyTemplate.CSAT).getFirst();

    assertThat(draft.type()).isEqualTo(QuestionType.RATING);
    assertThat(draft.range()).contains(new ScaleRange(1, 5));
    assertThat(draft.labels().min()).contains("Muito insatisfeito");
    assertThat(draft.labels().max()).contains("Muito satisfeito");
  }

  @Test
  @DisplayName("CES 2.0: afirmação de facilidade com concordância de 1 a 7")
  void ces() {
    var draft = TemplateQuestions.draftsFor(SurveyTemplate.CES).getFirst();

    assertThat(draft.type()).isEqualTo(QuestionType.SCALE);
    assertThat(draft.range()).contains(new ScaleRange(1, 7));
    assertThat(draft.labels().min()).contains("Discordo totalmente");
    assertThat(draft.labels().max()).contains("Concordo totalmente");
  }

  @ParameterizedTest
  @EnumSource(SurveyTemplate.class)
  @DisplayName("Cada modelo é uma pergunta, sem condição, publicável assim que houver disparo")
  void publicavel_sem_edicao(SurveyTemplate template) {
    var version = SurveyVersion.create(SurveyId.generate(), 1);
    var drafts = TemplateQuestions.draftsFor(template);

    drafts.forEach(version::addQuestion);
    version.defineTrigger(TriggerFactory.anOpenTrigger().build());

    assertThat(drafts).hasSize(1);
    assertThat(drafts.getFirst().condition()).isEmpty();
    assertThat(version.publicationImpediments()).isEmpty();
  }
}
