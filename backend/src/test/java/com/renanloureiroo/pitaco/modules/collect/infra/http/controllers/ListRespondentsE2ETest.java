package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
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
@DisplayName("GET /applications/{applicationId}/respondents")
class ListRespondentsE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final Instant FIRST = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-08T11:00:00Z");

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired RespondentRepository respondents;
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
    uri = "/applications/" + applicationId.value() + "/respondents";
  }

  private PageResponseDTO<RespondentResponseDTO> list(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<RespondentResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  private long countOf(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @Test
  @DisplayName("Devolve os dois tipos de identificação, e cada linha bate com o banco")
  void caminho_feliz() {
    var byDevice =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByDevice("9f3c1b7a-4e28-4d05-8a61-2b7f0c9e5d34")
            .firstSeenAt(FIRST)
            .buildSavedIn(respondents);
    var byReference =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference("user-8821")
            .firstSeenAt(FIRST)
            .lastSeenAt(SECOND)
            .buildSavedIn(respondents);

    var page = list("");

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.items())
        .extracting(RespondentResponseDTO::id)
        .containsExactly(byReference.id().value(), byDevice.id().value());
    assertThat(page.items())
        .extracting(RespondentResponseDTO::identityKind)
        .containsExactly(RespondentIdentityKind.APP_REFERENCE, RespondentIdentityKind.DEVICE);

    var item = page.items().get(0);
    assertThat(item.identityValue()).isEqualTo(storedValue(byReference));
    assertThat(item.firstSeenAt()).isEqualTo(FIRST);
    assertThat(item.lastSeenAt()).isEqualTo(SECOND);
  }

  private String storedValue(Respondent respondent) {
    return jdbc
        .sql("select identity_value from respondents where id = :id")
        .param("id", respondent.id().value())
        .query(String.class)
        .single();
  }

  @Test
  @DisplayName("Não devolve respondente de outra aplicação")
  void isola_as_aplicacoes() {
    var outra = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));

    var mine = RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);
    RespondentFactory.aRespondent().forApplication(outra.id()).buildSavedIn(respondents);

    var page = list("");

    assertThat(page.items())
        .extracting(RespondentResponseDTO::id)
        .containsExactly(mine.id().value());
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação sem nenhum respondente devolve página vazia com total 0")
  void aplicacao_sem_respondente() {
    var page = list("");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Página além do fim devolve vazio com o total correto")
  void pagina_alem_do_fim() {
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("a")
        .buildSavedIn(respondents);
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("b")
        .buildSavedIn(respondents);

    var page = list("?page=9&size=2");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Parâmetros de paginação fora dos limites respondem 400")
  void recusa_parametros_invalidos() {
    expectBadRequest("?page=-1");
    expectBadRequest("?size=0");
    expectBadRequest("?size=101");
  }

  private void expectBadRequest(String query) {
    client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
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
        .uri("/applications/" + UUID.randomUUID() + "/respondents")
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
  @DisplayName("Nenhum caminho, feliz ou de recusa, altera o estado gravado")
  void nao_altera_o_estado() {
    var respondent =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .firstSeenAt(FIRST)
            .lastSeenAt(SECOND)
            .buildSavedIn(respondents);

    list("");
    client.get().uri(uri + "?size=0").exchange().expectStatus().isBadRequest();
    client.get().uri(uri).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri("/applications/" + UUID.randomUUID() + "/respondents")
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(countOf("respondents")).isEqualTo(1);
    assertThat(
            jdbc.sql("select last_seen_at from respondents where id = :id")
                .param("id", respondent.id().value())
                .query(Instant.class)
                .single())
        .isEqualTo(SECOND);
  }
}
