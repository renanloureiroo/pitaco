package com.renanloureiroo.pitaco.infra.http.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renanloureiroo.pitaco.infra.http.error.ApiExceptionHandler;
import com.renanloureiroo.pitaco.infra.http.security.AdminSurfaceInterceptor;
import com.renanloureiroo.pitaco.infra.http.security.ApiKeyAuthenticationInterceptor;
import com.renanloureiroo.pitaco.modules.app.application.usecases.AuthenticateApiKeyUseCase;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApiKeyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import com.renanloureiroo.pitaco.testsupport.transaction.DirectTransactor;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// A cadeia real de interceptors sobre fakes, sem contexto Spring: é aqui que a ordem entre
// origem, autenticação e chave é provada com limites baixos. O E2E de contexto compartilhado
// só confirma que a cadeia está registrada na aplicação.
@DisplayName("Cadeia de rate limit da superfície pública")
class RateLimitInterceptorChainTest {

  private static final String HEADER = "X-Pitaco-Key";

  @RestController
  static class Surface {
    @PostMapping("/collect/ping")
    String collect() {
      return "ok";
    }

    @GetMapping("/applications/ping")
    String admin() {
      return "ok";
    }
  }

  private MockMvc mockMvc;
  private String key;
  private String otherKey;

  @BeforeEach
  void setUp() {
    var applications = new InMemoryApplicationRepository();
    var apiKeys = new InMemoryApiKeyRepository();
    var application = ApplicationFactory.anApplication().buildSavedIn(applications);

    var issued = ApiKeyFactory.anApiKey().forApplication(application.id()).issue();
    apiKeys.create(issued.apiKey());
    key = issued.plainSecret();

    var other = ApiKeyFactory.anApiKey().forApplication(application.id()).issue();
    apiKeys.create(other.apiKey());
    otherKey = other.plainSecret();

    var authenticate = new AuthenticateApiKeyUseCase(apiKeys, applications, new DirectTransactor());

    mockMvc =
        MockMvcBuilders.standaloneSetup(new Surface())
            .addMappedInterceptors(
                new String[] {"/collect/**"},
                new OriginRateLimitInterceptor(
                    new FixedWindowRateLimiter(5, Duration.ofMinutes(1)),
                    OriginResolver.of(
                        "CF-Connecting-IP",
                        List.of("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12"))),
                new ApiKeyAuthenticationInterceptor(authenticate),
                new ApiKeyRateLimitInterceptor(
                    new FixedWindowRateLimiter(3, Duration.ofMinutes(1))))
            .addMappedInterceptors(new String[] {"/applications/**"}, new AdminSurfaceInterceptor())
            .setControllerAdvice(new ApiExceptionHandler(Optional.empty()))
            .build();
  }

  private static MockHttpServletRequestBuilder collect(String key, String origin) {
    return post("/collect/ping")
        .header(HEADER, key)
        .with(
            request -> {
              request.setRemoteAddr(origin);
              return request;
            });
  }

  @Test
  @DisplayName("A quarta chamada da mesma chave responde 429 com Retry-After e code estável")
  void estoura_por_chave() throws Exception {
    for (var i = 0; i < 3; i++) {
      mockMvc.perform(collect(key, "10.0.0.1")).andExpect(status().isOk());
    }

    mockMvc
        .perform(collect(key, "10.0.0.1"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));
  }

  @Test
  @DisplayName("Uma chave esgotada não afeta outra chave da mesma aplicação")
  void chave_esgotada_nao_afeta_a_outra() throws Exception {
    for (var i = 0; i < 3; i++) {
      mockMvc.perform(collect(key, "10.0.0.1")).andExpect(status().isOk());
    }
    mockMvc.perform(collect(key, "10.0.0.2")).andExpect(status().isTooManyRequests());

    mockMvc.perform(collect(otherKey, "10.0.0.3")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("A origem estoura antes da autenticação: chave desconhecida também conta")
  void estoura_por_origem_antes_da_chave() throws Exception {
    for (var i = 0; i < 5; i++) {
      mockMvc.perform(collect("pit_desconhecida", "10.0.0.9")).andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(collect("pit_desconhecida", "10.0.0.9"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("rate_limit.exceeded"));

    mockMvc.perform(collect("pit_desconhecida", "10.0.0.10")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Origem esgotada barra até a chave válida que ainda tinha saldo")
  void origem_esgotada_barra_a_chave_valida() throws Exception {
    for (var i = 0; i < 5; i++) {
      mockMvc
          .perform(collect("pit_desconhecida", "10.0.0.20"))
          .andExpect(status().isUnauthorized());
    }

    mockMvc.perform(collect(otherKey, "10.0.0.20")).andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("Atrás de proxy confiável, trocar o começo do X-Forwarded-For não foge do limite")
  void forjar_o_primeiro_valor_nao_foge() throws Exception {
    for (var i = 0; i < 5; i++) {
      mockMvc
          .perform(
              collect("pit_desconhecida", "10.0.0.40")
                  .header("X-Forwarded-For", "198.51.100." + i + ", 203.0.113.9"))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            collect("pit_desconhecida", "10.0.0.40")
                .header("X-Forwarded-For", "198.51.100.99, 203.0.113.9"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("Conexão direta com cabeçalhos forjados conta pelo endereço real")
  void conexao_direta_ignora_cabecalhos() throws Exception {
    for (var i = 0; i < 5; i++) {
      mockMvc
          .perform(
              collect("pit_desconhecida", "203.0.113.60")
                  .header("CF-Connecting-IP", "198.51.100." + i)
                  .header("X-Forwarded-For", "198.51.100." + i))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            collect("pit_desconhecida", "203.0.113.60").header("CF-Connecting-IP", "198.51.100.77"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("O limite não alcança a superfície administrativa")
  void nao_alcanca_a_superficie_administrativa() throws Exception {
    for (var i = 0; i < 10; i++) {
      mockMvc
          .perform(
              get("/applications/ping")
                  .with(
                      request -> {
                        request.setRemoteAddr("10.0.0.30");
                        return request;
                      }))
          .andExpect(status().isOk());
    }
  }
}
