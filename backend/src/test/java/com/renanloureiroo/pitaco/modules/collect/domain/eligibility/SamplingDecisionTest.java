package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SamplingDecision")
class SamplingDecisionTest {

  private static final RespondentIdentity IDENTITY =
      new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, "u-8f1c");

  @Test
  @DisplayName("Proporção zero recusa todo mundo")
  void proporcao_zero_recusa_todos() {
    var rate = SamplingRate.of(0.0);

    assertThat(
            IntStream.range(0, 200)
                .mapToObj(i -> new RespondentIdentity(RespondentIdentityKind.DEVICE, "d-" + i))
                .anyMatch(identity -> SamplingDecision.accepts(SurveyId.generate(), identity, rate)))
        .isFalse();
  }

  @Test
  @DisplayName("Proporção um aceita todo mundo")
  void proporcao_um_aceita_todos() {
    var rate = SamplingRate.of(1.0);

    assertThat(
            IntStream.range(0, 200)
                .mapToObj(i -> new RespondentIdentity(RespondentIdentityKind.DEVICE, "d-" + i))
                .allMatch(identity -> SamplingDecision.accepts(SurveyId.generate(), identity, rate)))
        .isTrue();
  }

  @Test
  @DisplayName("A mesma dupla pesquisa–respondente dá sempre a mesma decisão")
  void decisao_e_estavel() {
    var surveyId = SurveyId.generate();
    var rate = SamplingRate.of(0.5);

    var decisions =
        IntStream.range(0, 5)
            .mapToObj(attempt -> SamplingDecision.accepts(surveyId, IDENTITY, rate))
            .distinct()
            .toList();

    assertThat(decisions).hasSize(1);
  }

  @Test
  @DisplayName("A chave é pesquisa–respondente: republicar não re-sorteia ninguém")
  void republicar_nao_re_sorteia() {
    var surveyId = SurveyId.generate();
    var rate = SamplingRate.of(0.5);

    // Não há versão na assinatura: por construção, uma versão nova não muda a decisão.
    assertThat(SamplingDecision.accepts(surveyId, IDENTITY, rate))
        .isEqualTo(SamplingDecision.accepts(surveyId, IDENTITY, rate));
  }

  @Test
  @DisplayName("Pesquisas diferentes sorteiam de forma independente")
  void pesquisas_diferentes_sao_independentes() {
    var rate = SamplingRate.of(0.5);

    var accepted =
        IntStream.range(0, 200)
            .filter(i -> SamplingDecision.accepts(SurveyId.generate(), IDENTITY, rate))
            .count();

    assertThat(accepted).isBetween(60L, 140L);
  }

  @Test
  @DisplayName("Respondentes diferentes sorteiam de forma independente")
  void respondentes_diferentes_sao_independentes() {
    var surveyId = SurveyId.generate();
    var rate = SamplingRate.of(0.5);

    var accepted =
        IntStream.range(0, 200)
            .filter(
                i ->
                    SamplingDecision.accepts(
                        surveyId,
                        new RespondentIdentity(RespondentIdentityKind.DEVICE, "d-" + i),
                        rate))
            .count();

    assertThat(accepted).isBetween(60L, 140L);
  }
}
