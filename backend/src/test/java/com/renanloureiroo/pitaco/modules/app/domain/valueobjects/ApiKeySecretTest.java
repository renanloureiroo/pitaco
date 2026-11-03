package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiKeySecret")
class ApiKeySecretTest {

  @Test
  void o_segredo_em_claro_comeca_pelo_prefixo_publico() {
    var generated = ApiKeySecret.generate();

    assertThat(generated.secret().prefix()).startsWith("pit_");
    assertThat(generated.plainText()).startsWith(generated.secret().prefix() + "_");
  }

  @Test
  void o_segredo_em_claro_tem_prefixo_e_parte_aleatoria() {
    // O alfabeto Base64 URL-safe inclui "_", então a forma é conferida pelo desenho inteiro, e
    // não por contagem de separadores.
    assertThat(ApiKeySecret.generate().plainText()).matches("^pit_[0-9a-f]{8}_[A-Za-z0-9_-]{43}$");
  }

  @Test
  void duas_geracoes_produzem_segredos_e_prefixos_distintos() {
    var first = ApiKeySecret.generate();
    var second = ApiKeySecret.generate();

    assertThat(first.plainText()).isNotEqualTo(second.plainText());
    assertThat(first.secret().prefix()).isNotEqualTo(second.secret().prefix());
    assertThat(first.secret().hash()).isNotEqualTo(second.secret().hash());
  }

  @Test
  void o_hash_guardado_e_o_do_segredo_em_claro() {
    var generated = ApiKeySecret.generate();

    assertThat(ApiKeySecret.hashOf(generated.plainText())).isEqualTo(generated.secret().hash());
  }

  @Test
  void o_hash_tem_64_caracteres_hexadecimais() {
    assertThat(ApiKeySecret.hashOf("qualquer segredo"))
        .hasSize(64)
        .matches("^[0-9a-f]{64}$");
  }

  @Test
  void o_hash_e_deterministico() {
    assertThat(ApiKeySecret.hashOf("qualquer segredo"))
        .isEqualTo(ApiKeySecret.hashOf("qualquer segredo"));
  }

  @Test
  void nao_imprime_o_hash() {
    var secret = ApiKeySecret.generate().secret();

    assertThat(secret).hasToString(secret.prefix());
  }
}
