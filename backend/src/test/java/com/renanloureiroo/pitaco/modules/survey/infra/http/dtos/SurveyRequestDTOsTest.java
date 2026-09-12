package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DTOs de entrada da pesquisa")
class SurveyRequestDTOsTest {

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

  private static Map<String, String> violationsOf(Object dto) {
    return validator.validate(dto).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  @Test
  void aceita_nome_valido_na_criacao() {
    assertThat(violationsOf(new CreateSurveyRequestDTO("NPS pós-checkout"))).isEmpty();
    assertThat(violationsOf(new CreateSurveyRequestDTO("a".repeat(120)))).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("A mensagem espelha a do value object SurveyName")
  void recusa_nome_ausente_na_criacao(String invalid) {
    assertThat(violationsOf(new CreateSurveyRequestDTO(invalid)))
        .containsEntry("name", "Nome é obrigatório");
  }

  @Test
  void recusa_nome_longo_demais_na_criacao() {
    assertThat(violationsOf(new CreateSurveyRequestDTO("a".repeat(121))))
        .containsEntry("name", "Nome não pode passar de 120 caracteres");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("Na edição, nome enviado em branco espelha a mensagem de SurveyName")
  void recusa_nome_em_branco_na_edicao(String invalid) {
    assertThat(violationsOf(new UpdateSurveyRequestDTO(Optional.of(invalid), null, null, null)))
        .containsEntry("name", "Nome é obrigatório");
  }

  @Test
  void recusa_nome_longo_demais_na_edicao() {
    assertThat(
            violationsOf(
                new UpdateSurveyRequestDTO(Optional.of("a".repeat(121)), null, null, null)))
        .containsEntry("name", "Nome não pode passar de 120 caracteres");
  }

  @Test
  void converte_a_criacao_em_input() {
    var applicationId = UUID.randomUUID().toString();

    var input = new CreateSurveyRequestDTO("NPS").toInput(applicationId);

    assertThat(input.applicationId()).isEqualTo(applicationId);
    assertThat(input.name()).isEqualTo("NPS");
  }

  @Test
  void converte_a_edicao_em_input() {
    var input =
        new UpdateSurveyRequestDTO(Optional.of("Outro"), null, null, null).toInput("app", "survey");

    assertThat(input.applicationId()).isEqualTo("app");
    assertThat(input.surveyId()).isEqualTo("survey");
    assertThat(input.name()).contains("Outro");
  }

  @Test
  void a_listagem_aceita_a_consulta_sem_nenhum_parametro() {
    assertThat(violationsOf(new ListSurveysQueryDTO(null, null))).isEmpty();
    assertThat(violationsOf(new ListSurveysQueryDTO(0, 1))).isEmpty();
    assertThat(violationsOf(new ListSurveysQueryDTO(0, 100))).isEmpty();
  }

  @Test
  void a_listagem_recusa_pagina_negativa() {
    assertThat(violationsOf(new ListSurveysQueryDTO(-1, null)))
        .containsEntry("page", "Página não pode ser negativa");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 101})
  void a_listagem_recusa_tamanho_fora_dos_limites(int size) {
    assertThat(violationsOf(new ListSurveysQueryDTO(null, size)))
        .containsEntry("size", "Tamanho de página deve estar entre 1 e 100");
  }

  @Test
  @DisplayName("Os padrões page=0 e size=20 são aplicados por toInput")
  void a_listagem_aplica_os_padroes() {
    var input = new ListSurveysQueryDTO(null, null).toInput("app");

    assertThat(input.page()).isZero();
    assertThat(input.size()).isEqualTo(20);
    assertThat(input.applicationId()).isEqualTo("app");
  }

  @Test
  void a_listagem_preserva_a_pagina_e_o_tamanho_informados() {
    var input = new ListSurveysQueryDTO(3, 50).toInput("app");

    assertThat(input.page()).isEqualTo(3);
    assertThat(input.size()).isEqualTo(50);
  }

  @Test
  void a_listagem_de_versoes_aplica_os_mesmos_padroes() {
    var input = new ListSurveyVersionsQueryDTO(null, null).toInput("app", "survey");

    assertThat(input.page()).isZero();
    assertThat(input.size()).isEqualTo(20);
    assertThat(input.surveyId()).isEqualTo("survey");
  }

  @Test
  @DisplayName("Não se impõe formato ao identificador na borda: isso vazaria o formato interno")
  void nao_impoe_formato_ao_identificador() {
    assertThat(new ListSurveysQueryDTO(null, null).toInput("nao-e-um-id").applicationId())
        .isEqualTo("nao-e-um-id");
  }
}
