package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListApplicationsUseCase")
class ListApplicationsUseCaseTest {

  private InMemoryApplicationRepository applications;
  private ListApplicationsUseCase useCase;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    useCase = new ListApplicationsUseCase(applications);
  }

  private ListApplicationsUseCase.Input input(Optional<Status> status, int page, int size) {
    return new ListApplicationsUseCase.Input(status, page, size);
  }

  private ListApplicationsUseCase.Input input() {
    return input(Optional.empty(), 0, 20);
  }

  @Test
  @DisplayName("Sem filtro devolve ativas e inativas")
  void sem_filtro_devolve_ativas_e_inativas() {
    ApplicationFactory.anApplication().withSlug("ativa").buildSavedIn(applications);
    ApplicationFactory.anApplication().withSlug("inativa").inactive().buildSavedIn(applications);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(ListApplicationsUseCase.Item::status)
        .containsExactlyInAnyOrder(Status.ACTIVE, Status.INACTIVE);
    assertThat(output.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Cada item traz identificador, slug, nome, estado e instante de criação")
  void descreve_a_aplicacao() {
    var application =
        ApplicationFactory.anApplicationWithPolicies()
            .withName("Acme App")
            .buildSavedIn(applications);

    var item = useCase.execute(input()).items().getFirst();

    assertThat(item.id()).isEqualTo(application.id().value());
    assertThat(item.slug()).isEqualTo("acme-app");
    assertThat(item.name()).isEqualTo("Acme App");
    assertThat(item.status()).isEqualTo(Status.ACTIVE);
    assertThat(item.createdAt()).isEqualTo(application.getCreatedAt());
  }

  @Test
  @DisplayName("Filtra por cada estado, com o total refletindo o filtro")
  void filtra_por_estado() {
    var active = ApplicationFactory.anApplication().withSlug("ativa").buildSavedIn(applications);
    var inactive =
        ApplicationFactory.anApplication()
            .withSlug("inativa")
            .inactive()
            .buildSavedIn(applications);

    var onlyActive = useCase.execute(input(Optional.of(Status.ACTIVE), 0, 20));
    var onlyInactive = useCase.execute(input(Optional.of(Status.INACTIVE), 0, 20));

    assertThat(onlyActive.items())
        .extracting(ListApplicationsUseCase.Item::id)
        .containsExactly(active.id().value());
    assertThat(onlyActive.total()).isEqualTo(1);
    assertThat(onlyInactive.items())
        .extracting(ListApplicationsUseCase.Item::id)
        .containsExactly(inactive.id().value());
    assertThat(onlyInactive.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Devolve da mais recente para a mais antiga")
  void ordena_da_mais_recente_para_a_mais_antiga() {
    var created = ApplicationFactory.anApplication().buildBatchSavedIn(applications, 3);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(ListApplicationsUseCase.Item::id)
        .containsExactly(
            created.get(2).id().value(), created.get(1).id().value(), created.get(0).id().value());
  }

  @Test
  @DisplayName("Criadas no mesmo instante desempatam por identificador, sem repetir nem omitir")
  void desempata_criadas_no_mesmo_instante() {
    var sameInstant = ApplicationFactory.BATCH_FIRST_CREATED_AT;
    var first =
        ApplicationFactory.anApplication()
            .withSlug("app-a")
            .withId(ApplicationId.of("00000000-0000-4000-8000-000000000001"))
            .createdAt(sameInstant)
            .buildSavedIn(applications);
    var second =
        ApplicationFactory.anApplication()
            .withSlug("app-b")
            .withId(ApplicationId.of("00000000-0000-4000-8000-000000000002"))
            .createdAt(sameInstant)
            .buildSavedIn(applications);

    var page = useCase.execute(input(Optional.empty(), 0, 1));
    var next = useCase.execute(input(Optional.empty(), 1, 1));

    assertThat(page.items())
        .extracting(ListApplicationsUseCase.Item::id)
        .containsExactly(second.id().value());
    assertThat(next.items())
        .extracting(ListApplicationsUseCase.Item::id)
        .containsExactly(first.id().value());
  }

  @Test
  @DisplayName("Percorre as páginas sem repetir nem omitir, e conta o total do conjunto")
  void percorre_as_paginas() {
    ApplicationFactory.anApplication().buildBatchSavedIn(applications, 5);

    var first = useCase.execute(input(Optional.empty(), 0, 2));
    var second = useCase.execute(input(Optional.empty(), 1, 2));
    var third = useCase.execute(input(Optional.empty(), 2, 2));

    assertThat(first.total()).isEqualTo(5);
    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(
            Stream.of(first, second, third)
                .flatMap(output -> output.items().stream())
                .map(ListApplicationsUseCase.Item::id))
        .doesNotHaveDuplicates()
        .hasSize(5);
  }

  @Test
  @DisplayName("Página além do fim devolve lista vazia com o total correto")
  void pagina_alem_do_fim() {
    ApplicationFactory.anApplication().buildBatchSavedIn(applications, 3);

    var output = useCase.execute(input(Optional.empty(), 9, 20));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("Sem nenhuma aplicação devolve lista vazia com total zero, não erro")
  void conjunto_vazio() {
    var output = useCase.execute(input());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
    assertThat(output.page()).isZero();
    assertThat(output.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("Filtro que não encontra nada devolve vazio com total zero")
  void filtro_sem_resultado() {
    ApplicationFactory.anApplication().buildSavedIn(applications);

    var output = useCase.execute(input(Optional.of(Status.INACTIVE), 0, 20));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
  }

  @Test
  @DisplayName("Tamanho de página zero não quebra o cálculo de páginas")
  void tamanho_zero_nao_quebra() {
    ApplicationFactory.anApplication().buildBatchSavedIn(applications, 3);

    var output = useCase.execute(input(Optional.empty(), 0, 0));

    assertThat(output.totalPages()).isZero();
  }
}
