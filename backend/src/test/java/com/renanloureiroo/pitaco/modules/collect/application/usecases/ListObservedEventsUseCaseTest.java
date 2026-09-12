package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.ObservedEventOutput;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryObservedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListObservedEventsUseCase")
class ListObservedEventsUseCaseTest {

  private static final Instant FIRST = Instant.parse("2026-09-10T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-11T11:00:00Z");

  private InMemoryCollectApplicationScopeGateway applications;
  private InMemoryObservedEventRepository events;
  private ListObservedEventsUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    applications = new InMemoryCollectApplicationScopeGateway();
    events = new InMemoryObservedEventRepository();
    useCase = new ListObservedEventsUseCase(applications, events);
    applicationId = applications.anActiveApplication();
  }

  private ListObservedEventsUseCase.Input input(int page, int size) {
    return new ListObservedEventsUseCase.Input(applicationId.value(), page, size);
  }

  @Test
  @DisplayName("Devolve nome e instantes, do visto mais recentemente para o mais antigo")
  void ordena_por_ultima_ocorrencia() {
    events
        .withSeen(applicationId, "app.opened", FIRST, FIRST)
        .withSeen(applicationId, "checkout.completed", FIRST, SECOND);

    var output = useCase.execute(input(0, 20));

    assertThat(output.items())
        .extracting(ObservedEventOutput::name)
        .containsExactly("checkout.completed", "app.opened");
    assertThat(output.items().getFirst().firstSeenAt()).isEqualTo(FIRST);
    assertThat(output.items().getFirst().lastSeenAt()).isEqualTo(SECOND);
    assertThat(output.total()).isEqualTo(2);
    assertThat(output.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("Empate na última ocorrência é desfeito pelo nome")
  void desempata_pelo_nome() {
    events
        .withSeen(applicationId, "b.evento", FIRST, SECOND)
        .withSeen(applicationId, "a.evento", FIRST, SECOND);

    var output = useCase.execute(input(0, 20));

    assertThat(output.items())
        .extracting(ObservedEventOutput::name)
        .containsExactly("a.evento", "b.evento");
  }

  @Test
  @DisplayName("Pagina com o total do catálogo inteiro")
  void pagina() {
    events
        .withSeen(applicationId, "a.evento", FIRST, SECOND)
        .withSeen(applicationId, "b.evento", FIRST, FIRST)
        .withSeen(applicationId, "c.evento", FIRST.minusSeconds(60), FIRST.minusSeconds(60));

    var output = useCase.execute(input(1, 2));

    assertThat(output.items()).extracting(ObservedEventOutput::name).containsExactly("c.evento");
    assertThat(output.page()).isEqualTo(1);
    assertThat(output.size()).isEqualTo(2);
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(2);
  }

  @Test
  @DisplayName("Não devolve evento de outra aplicação")
  void isola_as_aplicacoes() {
    var outra = applications.anActiveApplication();
    events
        .withSeen(applicationId, "minha.acao", FIRST, FIRST)
        .withSeen(outra, "acao.alheia", FIRST, SECOND);

    var output = useCase.execute(input(0, 20));

    assertThat(output.items()).extracting(ObservedEventOutput::name).containsExactly("minha.acao");
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação inativa continua com o catálogo legível")
  void aplicacao_inativa_e_legivel() {
    var inactive = applications.anInactiveApplication();
    events.withSeen(inactive, "app.opened", FIRST, FIRST);

    var output =
        useCase.execute(new ListObservedEventsUseCase.Input(inactive.value(), 0, 20));

    assertThat(output.items()).hasSize(1);
  }

  @Test
  @DisplayName("Aplicação sem consulta nenhuma devolve página vazia")
  void aplicacao_sem_eventos() {
    var output = useCase.execute(input(0, 20));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
  }

  @Test
  @DisplayName("Aplicação inexistente ou identificador malformado é o mesmo 404")
  void aplicacao_inexistente() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ListObservedEventsUseCase.Input(UUID.randomUUID().toString(), 0, 20)))
        .isInstanceOf(ApplicationNotFound.class);
    assertThatThrownBy(
            () -> useCase.execute(new ListObservedEventsUseCase.Input("nao-e-uuid", 0, 20)))
        .isInstanceOf(ApplicationNotFound.class);
  }
}
