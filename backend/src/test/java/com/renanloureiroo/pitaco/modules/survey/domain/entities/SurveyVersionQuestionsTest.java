package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DisplayName("SurveyVersion — perguntas")
class SurveyVersionQuestionsTest {

  private SurveyVersion version;

  @BeforeEach
  void setUp() {
    version = SurveyVersion.create(SurveyId.generate(), 1);
  }

  private static Question.Draft draft(String statement) {
    return new Question.Draft(
        QuestionStatement.of(statement), QuestionType.FREE_TEXT, true, List.of(), Optional.empty());
  }

  private Question add(String statement) {
    return version.addQuestion(draft(statement));
  }

  @Test
  @DisplayName("Cada pergunta acrescentada entra na última posição, com chave nova")
  void acrescenta_na_ultima_posicao() {
    var first = add("Primeira");
    var second = add("Segunda");
    var third = add("Terceira");

    assertThat(first.getPosition()).isEqualTo(1);
    assertThat(second.getPosition()).isEqualTo(2);
    assertThat(third.getPosition()).isEqualTo(3);
    assertThat(version.getQuestions()).extracting(Question::getPosition).containsExactly(1, 2, 3);
    assertThat(version.getQuestions()).extracting(Question::getKey).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("Reescrever uma pergunta preserva a chave estável e a posição")
  void reescrever_preserva_chave_e_posicao() {
    add("Primeira");
    var second = add("Segunda");

    version.updateQuestion(second.id(), draft("Segunda, corrigida"));

    var updated = version.question(second.id()).orElseThrow();
    assertThat(updated.getKey()).isEqualTo(second.getKey());
    assertThat(updated.getPosition()).isEqualTo(2);
    assertThat(updated.getStatement()).isEqualTo(QuestionStatement.of("Segunda, corrigida"));
  }

  @Test
  void reescrever_pergunta_inexistente_e_recusado() {
    add("Primeira");

    var estranha = QuestionId.generate();

    assertThatThrownBy(() -> version.updateQuestion(estranha, draft("Outra")))
        .satisfies(erro -> assertCode(erro, ErrorType.NOT_FOUND, "question.not_found"));
  }

  @Test
  @DisplayName("Remover a do meio recompacta as posições, sem buraco")
  void remover_recompacta_as_posicoes() {
    var first = add("Primeira");
    var second = add("Segunda");
    var third = add("Terceira");

    version.removeQuestion(second.id());

    assertThat(version.getQuestions()).extracting(Question::getPosition).containsExactly(1, 2);
    assertThat(version.getQuestions())
        .extracting(question -> question.id().value())
        .containsExactly(first.id().value(), third.id().value());
  }

  @Test
  void remover_pergunta_inexistente_e_recusado() {
    var estranha = QuestionId.generate();

    assertThatThrownBy(() -> version.removeQuestion(estranha))
        .satisfies(erro -> assertCode(erro, ErrorType.NOT_FOUND, "question.not_found"));
  }

  @Test
  @DisplayName("Reordenar aplica a nova ordem sem buraco nem repetição")
  void reordena_as_perguntas() {
    var first = add("Primeira");
    var second = add("Segunda");
    var third = add("Terceira");

    version.reorder(List.of(third.id(), first.id(), second.id()));

    assertThat(version.getQuestions()).extracting(Question::getPosition).containsExactly(1, 2, 3);
    assertThat(version.getQuestions())
        .extracting(question -> question.getStatement().value())
        .containsExactly("Terceira", "Primeira", "Segunda");
  }

  @Test
  @DisplayName("Reordenar exige permutação exata: lista incompleta é recusada")
  void recusa_lista_incompleta() {
    var first = add("Primeira");
    add("Segunda");

    assertThatThrownBy(() -> version.reorder(List.of(first.id())))
        .satisfies(erro -> assertCode(erro, ErrorType.VALIDATION, "question.order_invalid"));
  }

  @Test
  void recusa_lista_com_repeticao() {
    var first = add("Primeira");
    add("Segunda");

    assertThatThrownBy(() -> version.reorder(List.of(first.id(), first.id())))
        .satisfies(erro -> assertCode(erro, ErrorType.VALIDATION, "question.order_invalid"));
  }

  @Test
  void recusa_lista_com_pergunta_de_outra_versao() {
    var first = add("Primeira");
    add("Segunda");

    assertThatThrownBy(() -> version.reorder(List.of(first.id(), QuestionId.generate())))
        .satisfies(erro -> assertCode(erro, ErrorType.VALIDATION, "question.order_invalid"));
  }

  @Test
  @DisplayName("A ordem anterior fica intacta quando a permutação é recusada")
  void ordem_intacta_apos_recusa() {
    var first = add("Primeira");
    add("Segunda");

    assertThatThrownBy(() -> version.reorder(List.of(first.id())))
        .isInstanceOf(DomainException.class);

    assertThat(version.getQuestions())
        .extracting(question -> question.getStatement().value())
        .containsExactly("Primeira", "Segunda");
  }

  @Test
  @DisplayName("Versão publicada recusa as quatro escritas de pergunta")
  void versao_publicada_recusa_toda_escrita() {
    var question = add("Primeira");
    var published = publishedCopyOf(version);

    assertThatThrownBy(() -> published.addQuestion(draft("Nova")))
        .satisfies(SurveyVersionQuestionsTest::conteudoCongelado);
    assertThatThrownBy(() -> published.updateQuestion(question.id(), draft("Outra")))
        .satisfies(SurveyVersionQuestionsTest::conteudoCongelado);
    assertThatThrownBy(() -> published.removeQuestion(question.id()))
        .satisfies(SurveyVersionQuestionsTest::conteudoCongelado);
    assertThatThrownBy(() -> published.reorder(List.of(question.id())))
        .satisfies(SurveyVersionQuestionsTest::conteudoCongelado);
  }

  private static SurveyVersion publishedCopyOf(SurveyVersion draft) {
    return SurveyVersion.restore(
        draft.id(),
        draft.getSurveyId(),
        draft.getNumber(),
        SurveyVersionStatus.PUBLISHED,
        draft.getQuestions(),
        draft.trigger(),
        draft.getRules(),
        Optional.empty(),
        Optional.empty(),
        1,
        Optional.of(java.time.Instant.parse("2026-09-08T12:00:00Z")));
  }

  private static void conteudoCongelado(Throwable error) {
    assertCode(error, ErrorType.BUSINESS_RULE, "survey.content_frozen");
  }

  private static void assertCode(Throwable error, ErrorType type, String code) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(type);
    assertThat(domainError.code()).isEqualTo(code);
  }
}
