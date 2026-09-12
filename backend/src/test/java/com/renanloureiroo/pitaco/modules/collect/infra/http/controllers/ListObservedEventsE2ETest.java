package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedEventResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/events")
class ListObservedEventsE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final Instant FIRST = Instant.parse("2026-09-10T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-11T11:00:00Z");

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ObservedEventRepository events;
  @Autowired Transactor transactor;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
    uri = "/applications/" + applicationId.value() + "/events";
  }

  private void seen(ApplicationId owner, String name, Instant at) {
    transactor.runInTransaction(() -> events.record(owner, EventName.of(name), at));
  }

  private PageResponseDTO<ObservedEventResponseDTO> list(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(
            new ParameterizedTypeReference<PageResponseDTO<ObservedEventResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Devolve os eventos do visto mais recentemente para o mais antigo, como no banco")
  void caminho_feliz() {
    seen(applicationId, "app.opened", FIRST);
    seen(applicationId, "checkout.completed", FIRST);
    seen(applicationId, "checkout.completed", SECOND);

    var page = list("");

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.items())
        .extracting(ObservedEventResponseDTO::name)
        .containsExactly("checkout.completed", "app.opened");

    var item = page.items().getFirst();
    assertThat(item.firstSeenAt()).isEqualTo(FIRST);
    assertThat(item.lastSeenAt()).isEqualTo(SECOND);
    assertThat(
            jdbc.sql("select last_seen_at from application_events where name = :name")
                .param("name", "checkout.completed")
                .query(Instant.class)
                .single())
        .isEqualTo(SECOND);
  }

  @Test
  @DisplayName("Não devolve evento de outra aplicação")
  void isola_as_aplicacoes() {
    var outra = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));

    seen(applicationId, "minha.acao", FIRST);
    seen(outra.id(), "acao.alheia", SECOND);

    var page = list("");

    assertThat(page.items()).extracting(ObservedEventResponseDTO::name).containsExactly("minha.acao");
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação sem evento nenhum devolve página vazia com total 0")
  void aplicacao_sem_eventos() {
    var page = list("");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Pagina com o total do catálogo inteiro")
  void pagina() {
    seen(applicationId, "a.evento", SECOND);
    seen(applicationId, "b.evento", FIRST);
    seen(applicationId, "c.evento", FIRST.minusSeconds(60));

    var page = list("?page=1&size=2");

    assertThat(page.items()).extracting(ObservedEventResponseDTO::name).containsExactly("c.evento");
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
  }

  @Test
  @DisplayName("Parâmetros de paginação fora dos limites respondem 400")
  void recusa_parametros_invalidos() {
    for (var query : new String[] {"?page=-1", "?size=0", "?size=101"}) {
      client
          .get()
          .uri(uri + query)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
  }

  @Test
  @DisplayName("Chave de aplicação na superfície administrativa responde 403")
  void recusa_chave_de_aplicacao() {
    client
        .get()
        .uri(uri)
        .header(HEADER, "pit_qualquer")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");
  }

  @Test
  @DisplayName("Aplicação inexistente responde 404")
  void recusa_aplicacao_inexistente() {
    client
        .get()
        .uri("/applications/" + UUID.randomUUID() + "/events")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.not_found");
  }

  @Test
  @DisplayName("Nenhum caminho, feliz ou de recusa, altera o catálogo")
  void nao_altera_o_estado() {
    seen(applicationId, "app.opened", FIRST);

    list("");
    client.get().uri(uri + "?size=0").exchange().expectStatus().isBadRequest();
    client.get().uri(uri).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri("/applications/" + UUID.randomUUID() + "/events")
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(jdbc.sql("select count(*) from application_events").query(Long.class).single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select last_seen_at from application_events").query(Instant.class).single())
        .isEqualTo(FIRST);
  }
}
