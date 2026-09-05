package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateApplicationRequestDTOTest {

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

  private Map<String, String> violationsOf(CreateApplicationRequestDTO request) {
    return validator.validate(request).stream()
        .collect(
            Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first));
  }

  private CreateApplicationRequestDTO requestWith(String name, String slug) {
    return new CreateApplicationRequestDTO(name, slug, null, null, null);
  }

  @Test
  void aceita_apenas_o_nome() {
    assertThat(violationsOf(requestWith("Acme App", null))).isEmpty();
  }

  @Test
  void aceita_todos_os_campos_preenchidos() {
    var request = new CreateApplicationRequestDTO("Acme App", "acme-app", 15, 180, 30);

    assertThat(violationsOf(request)).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_nome_ausente(String invalid) {
    assertThat(violationsOf(requestWith(invalid, null)))
        .containsEntry("name", "Nome é obrigatório");
  }

  @Test
  void rejeita_nome_longo_demais() {
    assertThat(violationsOf(requestWith("a".repeat(121), null)))
        .containsEntry("name", "Nome não pode passar de 120 caracteres");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Acme App", "acme_app", "acme-", "-acme", "acme--app", "Acme"})
  void rejeita_slug_fora_do_formato(String invalid) {
    assertThat(violationsOf(requestWith("Acme App", invalid)))
        .containsEntry("slug", "Slug aceita apenas minúsculas, dígitos e hífen entre termos");
  }

  @Test
  void rejeita_slug_longo_demais() {
    assertThat(violationsOf(requestWith("Acme App", "a".repeat(51))))
        .containsEntry("slug", "Slug não pode passar de 50 caracteres");
  }

  @Test
  void rejeita_prazos_menores_que_um_dia() {
    var request = new CreateApplicationRequestDTO("Acme App", null, 0, -1, 0);

    assertThat(violationsOf(request))
        .containsEntry("quietPeriodDays", "O intervalo de descanso deve ser de ao menos um dia")
        .containsEntry("retentionDays", "O prazo de retenção deve ser de ao menos um dia")
        .containsEntry(
            "openTextRetentionDays",
            "O prazo de retenção de texto livre deve ser de ao menos um dia");
  }

  @Test
  void aponta_todos_os_campos_invalidos_de_uma_vez() {
    var request = new CreateApplicationRequestDTO("", "Acme App", 0, 0, 0);

    assertThat(violationsOf(request))
        .containsOnlyKeys(
            "name", "slug", "quietPeriodDays", "retentionDays", "openTextRetentionDays");
  }

  @Test
  void converte_para_a_entrada_do_caso_de_uso() {
    var input = new CreateApplicationRequestDTO("Acme App", "acme-app", 15, 180, 30).toInput();

    assertThat(input.name()).isEqualTo("Acme App");
    assertThat(input.slug()).isEqualTo("acme-app");
    assertThat(input.quietPeriodDays()).isEqualTo(15);
    assertThat(input.retentionDays()).isEqualTo(180);
    assertThat(input.openTextRetentionDays()).isEqualTo(30);
  }
}
