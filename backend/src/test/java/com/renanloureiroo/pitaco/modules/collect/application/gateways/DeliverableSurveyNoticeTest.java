package com.renanloureiroo.pitaco.modules.collect.application.gateways;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DeliverableSurveyOutput;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeliverableSurvey — aviso de texto livre")
class DeliverableSurveyNoticeTest {

  private static DeliverableQuestion question(QuestionType type) {
    return new DeliverableQuestion(
        QuestionKey.generate(),
        1,
        "Pergunta",
        type,
        false,
        List.of(),
        type == QuestionType.NPS ? Optional.of(ScaleRange.NPS) : Optional.empty());
  }

  private static DeliverableSurvey survey(Optional<String> notice, QuestionType... types) {
    return new DeliverableSurvey(
        SurveyId.generate(),
        SurveyVersionId.generate(),
        1,
        Arrays.stream(types).map(DeliverableSurveyNoticeTest::question).toList(),
        notice);
  }

  @Test
  @DisplayName("Só pede o recurso ao SDK quando há texto livre para o aviso acompanhar")
  void exige_texto_livre() {
    assertThat(survey(Optional.of("Aviso"), QuestionType.FREE_TEXT).hasFreeTextNotice()).isTrue();
    assertThat(survey(Optional.of("Aviso"), QuestionType.NPS).hasFreeTextNotice()).isFalse();
    assertThat(survey(Optional.empty(), QuestionType.FREE_TEXT).hasFreeTextNotice()).isFalse();
  }

  @Test
  @DisplayName("A entrega leva o aviso resolvido, ou desligado sem texto")
  void entrega() {
    var on = DeliverableSurveyOutput.of(survey(Optional.of("Aviso"), QuestionType.FREE_TEXT));
    var off = DeliverableSurveyOutput.of(survey(Optional.empty(), QuestionType.FREE_TEXT));

    assertThat(on.freeTextNotice().enabled()).isTrue();
    assertThat(on.freeTextNotice().text()).contains("Aviso");
    assertThat(off.freeTextNotice().enabled()).isFalse();
    assertThat(off.freeTextNotice().text()).isEmpty();
  }
}
