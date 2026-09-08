package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionKey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleRange;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Question")
class QuestionTest {

  private static final QuestionStatement STATEMENT = QuestionStatement.of("Qual sua nota?");

  private static QuestionOption option(String value, int position) {
    return new QuestionOption("Rótulo " + value, value, position);
  }

  private static Question.Draft draft(
      QuestionType type, List<QuestionOption> options, ScaleRange range) {
    return new Question.Draft(STATEMENT, type, true, options, Optional.ofNullable(range));
  }

  @Test
  @DisplayName("create gera chave nova e assume a posição pedida")
  void create_gera_chave_nova() {
    var question = Question.create(draft(QuestionType.FREE_TEXT, List.of(), null), 3);

    assertThat(question.id()).isNotNull();
    assertThat(question.getKey()).isNotNull();
    assertThat(question.getStatement()).isEqualTo(STATEMENT);
    assertThat(question.getType()).isEqualTo(QuestionType.FREE_TEXT);
    assertThat(question.getPosition()).isEqualTo(3);
    assertThat(question.isRequired()).isTrue();
    assertThat(question.getOptions()).isEmpty();
    assertThat(question.range()).isEmpty();
  }

  @Test
  void duas_perguntas_criadas_tem_chaves_distintas() {
    var first = Question.create(draft(QuestionType.FREE_TEXT, List.of(), null), 1);
    var second = Question.create(draft(QuestionType.FREE_TEXT, List.of(), null), 2);

    assertThat(first.getKey()).isNotEqualTo(second.getKey());
  }

  @Test
  @DisplayName("restore preserva a chave que veio do banco")
  void restore_preserva_a_chave() {
    var key = QuestionKey.generate();
    var id = QuestionId.generate();

    var question =
        Question.restore(
            id, key, STATEMENT, QuestionType.FREE_TEXT, 1, false, List.of(), Optional.empty());

    assertThat(question.id()).isEqualTo(id);
    assertThat(question.getKey()).isEqualTo(key);
    assertThat(question.isRequired()).isFalse();
  }

  @Test
  @DisplayName("Reescrever preserva a chave estável e a identidade da linha")
  void reescrever_preserva_a_chave() {
    var question = Question.create(draft(QuestionType.FREE_TEXT, List.of(), null), 1);
    var key = question.getKey();

    var rewritten =
        question.rewrittenAs(
            new Question.Draft(
                QuestionStatement.of("Outro enunciado"),
                QuestionType.SCALE,
                false,
                List.of(),
                Optional.of(new ScaleRange(1, 5))));

    assertThat(rewritten.getKey()).isEqualTo(key);
    assertThat(rewritten.id()).isEqualTo(question.id());
    assertThat(rewritten.getStatement()).isEqualTo(QuestionStatement.of("Outro enunciado"));
    assertThat(rewritten.getType()).isEqualTo(QuestionType.SCALE);
    assertThat(rewritten.getPosition()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"SINGLE_CHOICE", "MULTIPLE_CHOICE"})
  @DisplayName("Escolha sem nenhuma opção é aceita: é completude, não coerência")
  void escolha_sem_opcao_e_aceita(QuestionType type) {
    assertThatCode(() -> Question.create(draft(type, List.of(), null), 1))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"RATING", "SCALE", "NPS", "FREE_TEXT"})
  @DisplayName("Opção em tipo que não aceita opção é recusada")
  void recusa_opcao_em_tipo_que_nao_aceita(QuestionType type) {
    var range = type.requiresRange() ? new ScaleRange(0, 10) : null;

    assertThatThrownBy(() -> Question.create(draft(type, List.of(option("a", 1)), range), 1))
        .satisfies(erro -> assertCode(erro, "question.options_not_allowed"));
  }

  @Test
  @DisplayName("Duas opções com o mesmo valor são recusadas")
  void recusa_opcoes_repetidas() {
    var repeated = List.of(option("sim", 1), option("sim", 2));

    assertThatThrownBy(() -> Question.create(draft(QuestionType.SINGLE_CHOICE, repeated, null), 1))
        .satisfies(erro -> assertCode(erro, "question.options_duplicated"));
  }

  @Test
  void aceita_opcoes_com_valores_distintos() {
    var options = List.of(option("sim", 1), option("nao", 2));

    var question = Question.create(draft(QuestionType.SINGLE_CHOICE, options, null), 1);

    assertThat(question.getOptions()).hasSize(2);
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "FREE_TEXT"})
  @DisplayName("Faixa em tipo que não aceita faixa é recusada")
  void recusa_faixa_em_tipo_que_nao_aceita(QuestionType type) {
    assertThatThrownBy(() -> Question.create(draft(type, List.of(), new ScaleRange(1, 5)), 1))
        .satisfies(erro -> assertCode(erro, "question.scale_range_invalid"));
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"RATING", "SCALE", "NPS"})
  @DisplayName("Faixa ausente em tipo que exige faixa é recusada")
  void recusa_faixa_ausente_em_tipo_que_exige(QuestionType type) {
    assertThatThrownBy(() -> Question.create(draft(type, List.of(), null), 1))
        .satisfies(erro -> assertCode(erro, "question.scale_range_invalid"));
  }

  @Test
  @DisplayName("NPS só aceita a faixa fixa de 0 a 10")
  void nps_recusa_faixa_fora_de_zero_a_dez() {
    assertThatThrownBy(
            () -> Question.create(draft(QuestionType.NPS, List.of(), new ScaleRange(1, 5)), 1))
        .satisfies(erro -> assertCode(erro, "question.scale_range_invalid"));

    var nps = Question.create(draft(QuestionType.NPS, List.of(), new ScaleRange(0, 10)), 1);
    assertThat(nps.range()).contains(ScaleRange.NPS);
  }

  @Test
  @DisplayName("Mudar de posição não toca em nada mais")
  void muda_de_posicao_preservando_o_resto() {
    var question = Question.create(draft(QuestionType.FREE_TEXT, List.of(), null), 1);

    var moved = question.movedTo(7);

    assertThat(moved.getPosition()).isEqualTo(7);
    assertThat(moved.id()).isEqualTo(question.id());
    assertThat(moved.getKey()).isEqualTo(question.getKey());
  }

  @Test
  void rejeita_posicao_nao_positiva() {
    assertThatThrownBy(() -> Question.create(draft(QuestionType.FREE_TEXT, List.of(), null), 0))
        .satisfies(erro -> assertCode(erro, "question.position_invalid"));
  }

  private static void assertCode(Throwable error, String code) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo(code);
  }
}
