package com.renanloureiroo.pitaco.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SwaggerUiRedirectValve")
class SwaggerUiRedirectValveTest {

  private final SwaggerUiRedirectValve valve =
      new SwaggerUiRedirectValve("/api", "/swagger-ui.html");

  @ParameterizedTest
  @ValueSource(strings = {"/", "/api", "/api/"})
  @DisplayName("Redireciona as entradas da API, por GET e por HEAD")
  void redireciona_as_entradas(String path) {
    assertThat(valve.redirects("GET", path)).isTrue();
    assertThat(valve.redirects("HEAD", path)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/applications", "/api/collect/eligibility", "/qualquer", "/apis", ""})
  @DisplayName("Não toca em nenhuma outra rota")
  void nao_toca_nas_outras_rotas(String path) {
    assertThat(valve.redirects("GET", path)).isFalse();
  }

  @Test
  @DisplayName("Só GET e HEAD: uma escrita nas entradas segue o caminho normal")
  void so_leitura() {
    assertThat(valve.redirects("POST", "/api")).isFalse();
    assertThat(valve.redirects("DELETE", "/")).isFalse();
  }

  @Test
  @DisplayName("O destino é relativo e inclui o context-path, com ou sem barras sobrando")
  void destino_relativo() {
    assertThat(valve.location()).isEqualTo("/api/swagger-ui.html");
    assertThat(new SwaggerUiRedirectValve("/api/", "swagger-ui.html").location())
        .isEqualTo("/api/swagger-ui.html");
  }

  @Test
  @DisplayName("Sem context-path, só a raiz é entrada")
  void sem_context_path() {
    var semPrefixo = new SwaggerUiRedirectValve("", "/swagger-ui.html");

    assertThat(semPrefixo.redirects("GET", "/")).isTrue();
    assertThat(semPrefixo.location()).isEqualTo("/swagger-ui.html");
  }
}
