package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.testsupport.factories.ApiKeyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApiKeyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListApiKeysUseCase")
class ListApiKeysUseCaseTest {

  private InMemoryApplicationRepository applications;
  private InMemoryApiKeyRepository apiKeys;
  private ListApiKeysUseCase useCase;
  private Application application;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    apiKeys = new InMemoryApiKeyRepository();
    useCase = new ListApiKeysUseCase(applications, apiKeys);
    application = ApplicationFactory.anApplication().buildSavedIn(applications);
  }

  private ListApiKeysUseCase.Input input(Optional<ApiKeyStatus> status, int page, int size) {
    return new ListApiKeysUseCase.Input(application.id().value(), status, page, size);
  }

  private ListApiKeysUseCase.Input input() {
    return input(Optional.empty(), 0, 20);
  }

  @Test
  @DisplayName("Devolve as chaves da mais recente para a mais antiga")
  void ordena_da_mais_recente_para_a_mais_antiga() {
    var created =
        ApiKeyFactory.anApiKey().forApplication(application.id()).buildBatchSavedIn(apiKeys, 3);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(ListApiKeysUseCase.Item::id)
        .containsExactly(
            created.get(2).id().value(), created.get(1).id().value(), created.get(0).id().value());
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(1);
    assertThat(output.page()).isZero();
    assertThat(output.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("Cada item traz os dados públicos da chave, com o estado derivado")
  void descreve_a_chave_sem_segredo() {
    var apiKey =
        ApiKeyFactory.anApiKey()
            .forApplication(application.id())
            .withLabel("app Android")
            .buildSavedIn(apiKeys);

    var item = useCase.execute(input()).items().getFirst();

    assertThat(item.id()).isEqualTo(apiKey.id().value());
    assertThat(item.applicationId()).isEqualTo(application.id().value());
    assertThat(item.label()).isEqualTo("app Android");
    assertThat(item.prefix()).isEqualTo(apiKey.getSecret().prefix());
    assertThat(item.status()).isEqualTo(ApiKeyStatus.ACTIVE);
    assertThat(item.createdAt()).isEqualTo(apiKey.getCreatedAt());
    assertThat(item.revokedAt()).isEmpty();
  }

  @Test
  @DisplayName("Sem filtro devolve válidas e revogadas")
  void sem_filtro_devolve_todas() {
    ApiKeyFactory.anApiKey().forApplication(application.id()).buildSavedIn(apiKeys);
    ApiKeyFactory.anApiKey().forApplication(application.id()).revoked().buildSavedIn(apiKeys);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(ListApiKeysUseCase.Item::status)
        .containsExactlyInAnyOrder(ApiKeyStatus.ACTIVE, ApiKeyStatus.REVOKED);
    assertThat(output.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Filtra por estado, e a revogada traz o instante da revogação")
  void filtra_por_estado() {
    var active = ApiKeyFactory.anApiKey().forApplication(application.id()).buildSavedIn(apiKeys);
    var revoked =
        ApiKeyFactory.anApiKey().forApplication(application.id()).revoked().buildSavedIn(apiKeys);

    var onlyActive = useCase.execute(input(Optional.of(ApiKeyStatus.ACTIVE), 0, 20));
    var onlyRevoked = useCase.execute(input(Optional.of(ApiKeyStatus.REVOKED), 0, 20));

    assertThat(onlyActive.items()).extracting(ListApiKeysUseCase.Item::id)
        .containsExactly(active.id().value());
    assertThat(onlyActive.total()).isEqualTo(1);
    assertThat(onlyRevoked.items()).extracting(ListApiKeysUseCase.Item::id)
        .containsExactly(revoked.id().value());
    assertThat(onlyRevoked.items().getFirst().revokedAt()).contains(revoked.revokedAt().orElseThrow());
  }

  @Test
  @DisplayName("Não devolve chave de outra aplicação")
  void isola_as_aplicacoes() {
    var other = ApplicationFactory.anApplication().withSlug("outra-app").buildSavedIn(applications);
    var mine = ApiKeyFactory.anApiKey().forApplication(application.id()).buildSavedIn(apiKeys);
    ApiKeyFactory.anApiKey().forApplication(other.id()).buildSavedIn(apiKeys);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(ListApiKeysUseCase.Item::id)
        .containsExactly(mine.id().value());
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação sem nenhuma chave devolve lista vazia, não erro")
  void aplicacao_sem_chave_devolve_lista_vazia() {
    var output = useCase.execute(input());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
  }

  @Test
  @DisplayName("Percorre as páginas sem repetir nem omitir, e conta o total do conjunto")
  void percorre_as_paginas() {
    ApiKeyFactory.anApiKey().forApplication(application.id()).buildBatchSavedIn(apiKeys, 5);

    var first = useCase.execute(input(Optional.empty(), 0, 2));
    var second = useCase.execute(input(Optional.empty(), 1, 2));
    var third = useCase.execute(input(Optional.empty(), 2, 2));

    assertThat(first.items()).hasSize(2);
    assertThat(second.items()).hasSize(2);
    assertThat(third.items()).hasSize(1);
    assertThat(first.total()).isEqualTo(5);
    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(
            java.util.stream.Stream.of(first, second, third)
                .flatMap(output -> output.items().stream())
                .map(ListApiKeysUseCase.Item::id))
        .doesNotHaveDuplicates()
        .hasSize(5);
  }

  @Test
  @DisplayName("Página além do fim devolve lista vazia com o total correto")
  void pagina_alem_do_fim() {
    ApiKeyFactory.anApiKey().forApplication(application.id()).buildBatchSavedIn(apiKeys, 3);

    var output = useCase.execute(input(Optional.empty(), 9, 20));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação inativa lista normalmente: inatividade impede emitir, não enxergar")
  void aplicacao_inativa_lista() {
    var inactive =
        ApplicationFactory.anApplication()
            .withSlug("app-inativa")
            .inactive()
            .buildSavedIn(applications);
    var apiKey = ApiKeyFactory.anApiKey().forApplication(inactive.id()).buildSavedIn(apiKeys);

    var output =
        useCase.execute(
            new ListApiKeysUseCase.Input(inactive.id().value(), Optional.empty(), 0, 20));

    assertThat(output.items()).extracting(ListApiKeysUseCase.Item::id)
        .containsExactly(apiKey.id().value());
  }

  @Test
  @DisplayName("Aplicação inexistente e identificador malformado recusam do mesmo jeito")
  void recusa_aplicacao_que_nao_existe() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ListApiKeysUseCase.Input(
                        UUID.randomUUID().toString(), Optional.empty(), 0, 20)))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(error -> assertThat(((ApplicationNotFound) error).code())
            .isEqualTo("application.not_found"));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ListApiKeysUseCase.Input("nao-e-um-id", Optional.empty(), 0, 20)))
        .isInstanceOf(ApplicationNotFound.class)
        .satisfies(error -> assertThat(((ApplicationNotFound) error).code())
            .isEqualTo("application.not_found"));
  }

  @Test
  @DisplayName("Tamanho de página zero não quebra o cálculo de páginas")
  void tamanho_zero_nao_quebra() {
    ApiKeyFactory.anApiKey().forApplication(application.id()).buildBatchSavedIn(apiKeys, 3);

    var output = useCase.execute(input(Optional.empty(), 0, 0));

    assertThat(output.totalPages()).isZero();
  }
}
