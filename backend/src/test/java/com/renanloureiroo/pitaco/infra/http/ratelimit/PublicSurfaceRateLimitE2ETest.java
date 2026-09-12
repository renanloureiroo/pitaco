package com.renanloureiroo.pitaco.infra.http.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

// Mesmo contexto da suíte, com o limite por chave que a anotação @E2E fixa: prova que a cadeia
// está registrada na superfície pública. A ordem entre as camadas é coberta sem contexto em
// RateLimitInterceptorChainTest.
@E2E
@DisplayName("Superfície pública — limite de requisições")
class PublicSurfaceRateLimitE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String ELIGIBILITY = "/collect/eligibility";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired DatabaseCleaner database;
  @Autowired FixedWindowRateLimiter originRateLimiter;

  private String key;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));

    var issued = ApiKey.issue(ApplicationId.of(application.id().value()), ApiKeyLabel.of("app"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();
  }

  private RestTestClient.ResponseSpec call() {
    return call(new HttpHeaders());
  }

  private RestTestClient.ResponseSpec call(HttpHeaders extra) {
    return client
        .post()
        .uri(ELIGIBILITY)
        .header(HEADER, key)
        .headers(headers -> headers.addAll(extra))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EligibilityRequestDTO("checkout.completed", new RespondentDTO("u-1", null), null))
        .exchange();
  }

  @Test
  @DisplayName("Passado o limite por chave, a chamada seguinte responde 429 com Retry-After")
  void estoura_por_chave() {
    for (var i = 0; i < E2E.RATE_LIMIT_PER_KEY_CAPACITY; i++) {
      call().expectStatus().isOk();
    }

    var retryAfter =
        call()
            .expectStatus()
            .isEqualTo(429)
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("rate_limit.exceeded")
            .returnResult()
            .getResponseHeaders()
            .getFirst("Retry-After");

    assertThat(retryAfter).isNotNull();
    assertThat(Integer.parseInt(retryAfter)).isBetween(1, 60);
  }

  // O servidor real passa pelo ForwardedHeaderFilter antes do interceptor; é aqui que se prova
  // que a origem sai da requisição crua, e não do primeiro X-Forwarded-For que o filtro adota.
  @Test
  @DisplayName("Forjar o começo do X-Forwarded-For não abre uma origem nova")
  void origem_forjada_nao_abre_janela_nova() {
    var real = "203.0.113." + (1 + (int) (Math.random() * 200));

    for (var i = 0; i < 3; i++) {
      var forged = "198.51.100." + (10 + i);
      var headers = new HttpHeaders();
      headers.add("X-Forwarded-For", forged + ", " + real);
      call(headers).expectStatus().isOk();

      assertThat(originRateLimiter.tracks(forged)).isFalse();
    }

    assertThat(originRateLimiter.tracks(real)).isTrue();
  }

  @Test
  @DisplayName("Atrás do túnel, o CF-Connecting-IP é a origem")
  void cabecalho_da_cloudflare_e_a_origem() {
    var real = "203.0.113." + (1 + (int) (Math.random() * 200));
    var headers = new HttpHeaders();
    headers.add("CF-Connecting-IP", real);
    headers.add("X-Forwarded-For", "198.51.100.200");

    call(headers).expectStatus().isOk();

    assertThat(originRateLimiter.tracks(real)).isTrue();
    assertThat(originRateLimiter.tracks("198.51.100.200")).isFalse();
  }
}
