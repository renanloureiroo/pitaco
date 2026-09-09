package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListDisplaysQueryDTOTest {

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

  private Map<String, String> violationsOf(ListDisplaysQueryDTO query) {
    return validator.validate(query).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private ListDisplaysQueryDTO paging(Integer page, Integer size) {
    return new ListDisplaysQueryDTO(null, null, null, null, page, size);
  }

  private ListDisplaysQueryDTO period(Instant from, Instant to) {
    return new ListDisplaysQueryDTO(null, null, from, to, null, null);
  }

  @Test
  void aceita_a_consulta_sem_nenhum_parametro() {
    assertThat(violationsOf(paging(null, null))).isEmpty();
  }

  @Test
  void aceita_os_limites_de_tamanho_de_pagina() {
    assertThat(violationsOf(paging(0, 1))).isEmpty();
    assertThat(violationsOf(paging(0, 100))).isEmpty();
  }

  @Test
  void rejeita_pagina_negativa() {
    assertThat(violationsOf(paging(-1, null))).containsEntry("page", "Página não pode ser negativa");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 101})
  void rejeita_tamanho_de_pagina_fora_dos_limites(int size) {
    assertThat(violationsOf(paging(null, size)))
        .containsEntry("size", "Tamanho de página deve estar entre 1 e 100");
  }

  @Test
  void aceita_identificador_de_versao_em_uuid() {
    var query =
        new ListDisplaysQueryDTO(
            UUID.randomUUID().toString(), null, null, null, null, null);

    assertThat(violationsOf(query)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "nao-e-um-id", "8c2b5e14-3a97-4d60-b1f8"})
  void rejeita_identificador_de_versao_malformado(String versionId) {
    assertThat(violationsOf(new ListDisplaysQueryDTO(versionId, null, null, null, null, null)))
        .containsEntry("versionId", "Identificador de versão de pesquisa inválido");
  }

  @Test
  void aceita_periodo_ordenado_e_periodo_de_um_instante_so() {
    assertThat(violationsOf(period(START, END))).isEmpty();
    assertThat(violationsOf(period(START, START))).isEmpty();
    assertThat(violationsOf(period(START, null))).isEmpty();
    assertThat(violationsOf(period(null, END))).isEmpty();
  }

  @Test
  void rejeita_periodo_invertido_apontando_o_campo() {
    assertThat(violationsOf(period(END, START)))
        .containsEntry("periodOrdered", "Início do período não pode ser posterior ao fim");
  }

  @Test
  void aplica_os_padroes_quando_nada_e_informado() {
    var input = paging(null, null).toInput("app", "survey");

    assertThat(input.applicationId()).isEqualTo("app");
    assertThat(input.surveyId()).isEqualTo("survey");
    assertThat(input.page()).isZero();
    assertThat(input.size()).isEqualTo(20);
    assertThat(input.versionId()).isEmpty();
    assertThat(input.outcome()).isEmpty();
    assertThat(input.openedFrom()).isEmpty();
    assertThat(input.openedTo()).isEmpty();
  }

  @Test
  void converte_os_filtros_informados() {
    var versionId = UUID.randomUUID().toString();
    var query =
        new ListDisplaysQueryDTO(
            versionId, DisplayOutcomeFilter.DISMISSED, START, END, 3, 50);

    var input = query.toInput("app", "survey");

    assertThat(input.versionId()).contains(SurveyVersionId.of(versionId));
    assertThat(input.outcome()).contains(DisplayOutcome.DISMISSED);
    assertThat(input.openedFrom()).contains(START);
    assertThat(input.openedTo()).contains(END);
    assertThat(input.page()).isEqualTo(3);
    assertThat(input.size()).isEqualTo(50);
  }

  @Test
  void a_listagem_por_respondente_reaproveita_o_dto_e_ignora_a_versao() {
    var query =
        new ListDisplaysQueryDTO(
            UUID.randomUUID().toString(), DisplayOutcomeFilter.COMPLETED, START, END, 1, 10);

    var input = query.toRespondentInput("app", "respondent");

    assertThat(input.applicationId()).isEqualTo("app");
    assertThat(input.respondentId()).isEqualTo("respondent");
    assertThat(input.outcome()).contains(DisplayOutcome.COMPLETED);
    assertThat(input.openedFrom()).contains(START);
    assertThat(input.openedTo()).contains(END);
    assertThat(input.page()).isEqualTo(1);
    assertThat(input.size()).isEqualTo(10);
  }

  @Test
  void nao_impoe_formato_ao_identificador_da_pesquisa() {
    // Validar o formato aqui devolveria 400 e revelaria o formato interno do identificador.
    assertThat(paging(null, null).toInput("app", "nao-e-um-id").surveyId()).isEqualTo("nao-e-um-id");
  }
}
