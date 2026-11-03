package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.RespondentNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.RespondentDisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRespondentRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListRespondentDisplaysUseCase")
class ListRespondentDisplaysUseCaseTest {

  private static final Instant FIRST = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-08T11:00:00Z");
  private static final Instant THIRD = Instant.parse("2026-09-08T12:00:00Z");

  private InMemoryRespondentRepository respondents;
  private InMemorySurveyDisplayRepository displays;
  private ListRespondentDisplaysUseCase useCase;
  private ApplicationId applicationId;
  private Respondent respondent;

  @BeforeEach
  void setUp() {
    respondents = new InMemoryRespondentRepository();
    displays = new InMemorySurveyDisplayRepository();
    useCase = new ListRespondentDisplaysUseCase(respondents, displays);
    applicationId = ApplicationId.generate();
    respondent = RespondentFactory.aRespondent().forApplication(applicationId).buildSavedIn(respondents);
  }

  private ListRespondentDisplaysUseCase.Input input() {
    return input(Optional.empty(), Optional.empty(), Optional.empty(), 0, 20);
  }

  private ListRespondentDisplaysUseCase.Input input(
      Optional<DisplayOutcome> outcome,
      Optional<Instant> from,
      Optional<Instant> to,
      int page,
      int size) {
    return new ListRespondentDisplaysUseCase.Input(
        applicationId.value(), respondent.id().value(), outcome, from, to, page, size);
  }

  private DisplayId displayOf(SurveyId surveyId, Instant openedAt) {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forRespondent(respondent.id())
        .forSurvey(surveyId)
        .openedAt(openedAt)
        .buildSavedIn(displays)
        .id();
  }

  @Test
  @DisplayName("Exibições de duas pesquisas aparecem, cada uma apontando a sua")
  void exibicoes_de_duas_pesquisas() {
    var first = SurveyId.generate();
    var second = SurveyId.generate();
    var versionId = SurveyVersionId.generate();
    displays.withVersionNumber(versionId, 4);

    var older = displayOf(first, FIRST);
    var newer =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forRespondent(respondent.id())
            .forSurvey(second)
            .forVersion(versionId)
            .openedAt(SECOND)
            .buildSavedIn(displays)
            .id();

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(item -> item.display().id())
        .containsExactly(newer, older);
    assertThat(output.items())
        .extracting(RespondentDisplaySummaryOutput::surveyId)
        .containsExactly(second, first);
    assertThat(output.items().get(0).display().versionNumber()).isEqualTo(4);
    assertThat(output.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Respondente sem nenhuma exibição devolve lista vazia com total 0")
  void respondente_sem_exibicao() {
    var output = useCase.execute(input());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
  }

  @Test
  @DisplayName("Filtra por desfecho e por período inclusivo")
  void filtra() {
    var surveyId = SurveyId.generate();
    var dismissed =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forRespondent(respondent.id())
            .forSurvey(surveyId)
            .openedAt(SECOND)
            .dismissedAt(SECOND.plusSeconds(5))
            .buildSavedIn(displays)
            .id();
    var started = displayOf(surveyId, THIRD);

    var byOutcome =
        useCase.execute(
            input(
                Optional.of(DisplayOutcome.DISMISSED),
                Optional.empty(),
                Optional.empty(),
                0,
                20));
    assertThat(byOutcome.items())
        .extracting(item -> item.display().id())
        .containsExactly(dismissed);

    var byPeriod =
        useCase.execute(
            input(Optional.empty(), Optional.of(THIRD), Optional.of(THIRD), 0, 20));
    assertThat(byPeriod.items()).extracting(item -> item.display().id()).containsExactly(started);
  }

  @Test
  @DisplayName("Ordena por abertura desc com desempate por identificador desc")
  void ordena_e_desempata() {
    var surveyId = SurveyId.generate();
    var lower = displayWithId(surveyId, "11111111-1111-4111-8111-111111111111");
    var higher = displayWithId(surveyId, "99999999-9999-4999-8999-999999999999");
    var older = displayOf(surveyId, FIRST);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(item -> item.display().id())
        .containsExactly(higher, lower, older);
  }

  private DisplayId displayWithId(SurveyId surveyId, String id) {
    return SurveyDisplayFactory.aDisplay()
        .withId(DisplayId.of(id))
        .forApplication(applicationId)
        .forRespondent(respondent.id())
        .forSurvey(surveyId)
        .openedAt(THIRD)
        .buildSavedIn(displays)
        .id();
  }

  @Test
  @DisplayName("Não devolve exibição de outra aplicação")
  void isola_as_aplicacoes() {
    var surveyId = SurveyId.generate();
    var mine = displayOf(surveyId, SECOND);
    SurveyDisplayFactory.aDisplay()
        .forApplication(ApplicationId.generate())
        .forRespondent(respondent.id())
        .forSurvey(surveyId)
        .openedAt(THIRD)
        .buildSavedIn(displays);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(item -> item.display().id()).containsExactly(mine);
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Página além do fim devolve vazio com o total correto")
  void pagina_alem_do_fim() {
    var surveyId = SurveyId.generate();
    displayOf(surveyId, FIRST);
    displayOf(surveyId, SECOND);

    var output = useCase.execute(input(Optional.empty(), Optional.empty(), Optional.empty(), 9, 2));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Respondente inexistente, de outra aplicação ou malformado recusa igual")
  void recusa_respondente_fora_do_escopo() {
    var alheio =
        RespondentFactory.aRespondent()
            .forApplication(ApplicationId.generate())
            .buildSavedIn(respondents);

    assertThatThrownBy(() -> execute(UUID.randomUUID().toString()))
        .isInstanceOf(RespondentNotFound.class);
    assertThatThrownBy(() -> execute(alheio.id().value()))
        .isInstanceOf(RespondentNotFound.class);
    assertThatThrownBy(() -> execute("nao-e-um-id")).isInstanceOf(RespondentNotFound.class);
  }

  private void execute(String respondentId) {
    useCase.execute(
        new ListRespondentDisplaysUseCase.Input(
            applicationId.value(),
            respondentId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            0,
            20));
  }
}
