package com.renanloureiroo.pitaco.modules.results.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResultsQueryDTOTest {

  private static final Instant START = Instant.parse("2026-09-08T00:00:00Z");
  private static final Instant END = Instant.parse("2026-09-08T23:59:59Z");

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void startValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private Map<String, String> violationsOf(Object query) {
    return validator.validate(query).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  @Test
  void aceita_sem_nenhum_parametro_e_com_recorte_completo() {
    assertThat(violationsOf(new ResultsQueryDTO(null, null, null, null, null))).isEmpty();
    assertThat(violationsOf(new ResultsQueryDTO(START, END, "plano", "pro", 2))).isEmpty();
    assertThat(violationsOf(new ResultsQueryDTO(null, null, "plano", null, null))).isEmpty();
  }

  @Test
  void recusa_periodo_invertido() {
    assertThat(violationsOf(new ResultsQueryDTO(END, START, null, null, null)))
        .containsEntry("periodOrdered", "Início do período não pode ser posterior ao fim");
  }

  @Test
  void recusa_valor_de_atributo_sem_nome_e_nome_vazio() {
    assertThat(violationsOf(new ResultsQueryDTO(null, null, null, "pro", null)))
        .containsEntry("attributeValueScoped", "Valor do atributo exige o nome do atributo");
    assertThat(violationsOf(new ResultsQueryDTO(null, null, "  ", null, null)))
        .containsEntry("attributeNamed", "Nome do atributo não pode ser vazio");
  }

  @Test
  void recusa_versao_menor_que_um() {
    assertThat(violationsOf(new ResultsQueryDTO(null, null, null, null, 0)))
        .containsEntry("version", "Número de versão deve ser maior ou igual a 1");
  }

  @Test
  void paginacao_das_respostas_abertas_tem_limites() {
    assertThat(violationsOf(new OpenAnswersQueryDTO(null, null, null, null, null, null, -1, 101)))
        .containsKeys("page", "size");
    assertThat(violationsOf(new OpenAnswersQueryDTO(null, null, null, null, null, "x".repeat(201), 0, 20)))
        .containsKey("q");
  }

  @Test
  void o_recorte_vira_selecao_com_ausencia_de_valor_como_atributo_ausente() {
    var selection = new ResultsQueryDTO(START, END, "plano", null, 3).toSelection();

    assertThat(selection.attribute()).hasValue("plano");
    assertThat(selection.attributeValue()).isEmpty();
    assertThat(selection.toOutput().attributeAbsent()).isTrue();
    assertThat(selection.versionNumber()).hasValue(3);
  }
}
