package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.RespondentOutput;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRespondentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListRespondentsUseCase")
class ListRespondentsUseCaseTest {

  private static final Instant FIRST = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-08T11:00:00Z");

  private InMemoryCollectApplicationScopeGateway applications;
  private InMemoryRespondentRepository respondents;
  private ListRespondentsUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    applications = new InMemoryCollectApplicationScopeGateway();
    respondents = new InMemoryRespondentRepository();
    useCase = new ListRespondentsUseCase(applications, respondents);
    applicationId = applications.anActiveApplication();
  }

  private ListRespondentsUseCase.Input input(int page, int size) {
    return new ListRespondentsUseCase.Input(applicationId.value(), page, size);
  }

  private ListRespondentsUseCase.Input input() {
    return input(0, 20);
  }

  @Test
  @DisplayName("Distingue os dois tipos de identificação e devolve os instantes de contato")
  void distingue_os_tipos_de_identificacao() {
    var byReference =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference("user-8821")
            .firstSeenAt(FIRST)
            .lastSeenAt(SECOND)
            .buildSavedIn(respondents);
    var byDevice =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByDevice("device-1")
            .firstSeenAt(FIRST)
            .buildSavedIn(respondents);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(RespondentOutput::id)
        .containsExactly(byReference.id(), byDevice.id());
    assertThat(output.items())
        .extracting(RespondentOutput::identityKind)
        .containsExactly(RespondentIdentityKind.APP_REFERENCE, RespondentIdentityKind.DEVICE);
    assertThat(output.items().get(0).identityValue()).isEqualTo("user-8821");
    assertThat(output.items().get(0).firstSeenAt()).isEqualTo(FIRST);
    assertThat(output.items().get(0).lastSeenAt()).isEqualTo(SECOND);
    assertThat(output.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Ordena por último contato desc com desempate determinístico por identificador")
  void ordena_por_ultimo_contato() {
    var older =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference("a")
            .firstSeenAt(FIRST)
            .buildSavedIn(respondents);
    var newer =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference("b")
            .firstSeenAt(FIRST)
            .lastSeenAt(SECOND)
            .buildSavedIn(respondents);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(RespondentOutput::id)
        .containsExactly(newer.id(), older.id());

    var tied = respondents.findAll().stream().map(each -> each.id().value()).sorted().toList();
    assertThat(tied).hasSize(2);
  }

  @Test
  @DisplayName("Desempata pelo identificador desc quando o último contato coincide")
  void desempata_pelo_identificador() {
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("a")
        .firstSeenAt(FIRST)
        .buildSavedIn(respondents);
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("b")
        .firstSeenAt(FIRST)
        .buildSavedIn(respondents);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(respondent -> respondent.id().value())
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }

  @Test
  @DisplayName("Não devolve respondente de outra aplicação")
  void isola_as_aplicacoes() {
    var mine = RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);
    RespondentFactory.aRespondent()
        .forApplication(ApplicationId.generate())
        .buildSavedIn(respondents);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(RespondentOutput::id).containsExactly(mine.id());
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação sem nenhum respondente devolve lista vazia com total 0")
  void aplicacao_sem_respondente() {
    var output = useCase.execute(input());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
  }

  @Test
  @DisplayName("Página além do fim devolve vazio com o total correto, nunca erro")
  void pagina_alem_do_fim() {
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("a")
        .buildSavedIn(respondents);
    RespondentFactory.aRespondent()
        .forApplication(applicationId)
        .identifiedByReference("b")
        .buildSavedIn(respondents);

    var output = useCase.execute(input(9, 2));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Aplicação inexistente ou malformada recusa igual")
  void recusa_aplicacao_inexistente() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ListRespondentsUseCase.Input(UUID.randomUUID().toString(), 0, 20)))
        .isInstanceOf(ApplicationNotFound.class);
    assertThatThrownBy(
            () -> useCase.execute(new ListRespondentsUseCase.Input("nao-e-um-id", 0, 20)))
        .isInstanceOf(ApplicationNotFound.class);
  }

  @Test
  @DisplayName("Respondente de outra aplicação não é alcançável por identificador")
  void nao_alcanca_respondente_alheio() {
    var alheio =
        RespondentFactory.aRespondent()
            .forApplication(ApplicationId.generate())
            .buildSavedIn(respondents);

    assertThat(respondents.findById(alheio.id(), applicationId)).isEmpty();
    assertThat(respondents.findById(RespondentId.generate(), applicationId)).isEmpty();
  }
}
