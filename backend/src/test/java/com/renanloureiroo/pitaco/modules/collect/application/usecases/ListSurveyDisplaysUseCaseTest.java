package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyNotFoundInApplication;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListSurveyDisplaysUseCase")
class ListSurveyDisplaysUseCaseTest {

  private static final Instant FIRST = Instant.parse("2026-09-08T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-08T11:00:00Z");
  private static final Instant THIRD = Instant.parse("2026-09-08T12:00:00Z");

  private InMemorySurveyScopeGateway surveys;
  private InMemorySurveyDisplayRepository displays;
  private ListSurveyDisplaysUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyScopeGateway();
    displays = new InMemorySurveyDisplayRepository();
    useCase = new ListSurveyDisplaysUseCase(surveys, displays);
    applicationId = ApplicationId.generate();
    surveyId = surveys.aSurveyIn(applicationId);
  }

  private ListSurveyDisplaysUseCase.Input input() {
    return builder().build();
  }

  private InputBuilder builder() {
    return new InputBuilder();
  }

  private final class InputBuilder {
    private Optional<SurveyVersionId> versionId = Optional.empty();
    private Optional<DisplayOutcome> outcome = Optional.empty();
    private Optional<Instant> openedFrom = Optional.empty();
    private Optional<Instant> openedTo = Optional.empty();
    private int page;
    private int size = 20;
    private String survey = surveyId.value();

    InputBuilder forVersion(SurveyVersionId value) {
      this.versionId = Optional.of(value);
      return this;
    }

    InputBuilder withOutcome(DisplayOutcome value) {
      this.outcome = Optional.of(value);
      return this;
    }

    InputBuilder openedFrom(Instant value) {
      this.openedFrom = Optional.of(value);
      return this;
    }

    InputBuilder openedTo(Instant value) {
      this.openedTo = Optional.of(value);
      return this;
    }

    InputBuilder page(int value, int pageSize) {
      this.page = value;
      this.size = pageSize;
      return this;
    }

    InputBuilder forSurvey(String value) {
      this.survey = value;
      return this;
    }

    ListSurveyDisplaysUseCase.Input build() {
      return new ListSurveyDisplaysUseCase.Input(
          applicationId.value(), survey, versionId, outcome, openedFrom, openedTo, page, size);
    }
  }

  private DisplayId anOpenDisplayAt(Instant openedAt) {
    return SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .openedAt(openedAt)
        .buildSavedIn(displays)
        .id();
  }

  @Test
  @DisplayName("Devolve os três desfechos gravados, com o fechamento só nos finais")
  void devolve_os_tres_desfechos() {
    var versionId = SurveyVersionId.generate();
    displays.withVersionNumber(versionId, 3);

    var completed =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .openedAt(THIRD)
            .completedAt(THIRD.plusSeconds(30))
            .buildSavedIn(displays);
    var dismissed =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .openedAt(SECOND)
            .dismissedAt(SECOND.plusSeconds(10))
            .buildSavedIn(displays);
    var started = anOpenDisplayAt(FIRST);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(DisplaySummaryOutput::id)
        .containsExactly(completed.id(), dismissed.id(), started);
    assertThat(output.items())
        .extracting(DisplaySummaryOutput::outcome)
        .containsExactly(
            DisplayOutcome.COMPLETED, DisplayOutcome.DISMISSED, DisplayOutcome.STARTED);
    assertThat(output.items().get(0).closedAt()).contains(THIRD.plusSeconds(30));
    assertThat(output.items().get(2).closedAt()).isEmpty();
    assertThat(output.items().get(0).versionNumber()).isEqualTo(3);
    assertThat(output.items().get(0).versionId()).isEqualTo(versionId);
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(1);
    assertThat(output.page()).isZero();
    assertThat(output.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("Ordena por abertura desc com desempate determinístico por identificador desc")
  void ordena_e_desempata() {
    var sameInstant = Instant.parse("2026-09-08T13:00:00Z");
    var lower = displayWithId("11111111-1111-4111-8111-111111111111", sameInstant);
    var higher = displayWithId("99999999-9999-4999-8999-999999999999", sameInstant);
    var older = anOpenDisplayAt(FIRST);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(DisplaySummaryOutput::id)
        .containsExactly(higher, lower, older);
  }

  private DisplayId displayWithId(String id, Instant openedAt) {
    return SurveyDisplayFactory.aDisplay()
        .withId(DisplayId.of(id))
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .openedAt(openedAt)
        .buildSavedIn(displays)
        .id();
  }

  @Test
  @DisplayName("Pesquisa existente sem nenhuma exibição devolve página vazia com total 0")
  void pesquisa_sem_exibicao() {
    var output = useCase.execute(input());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
  }

  @Test
  @DisplayName("Filtra por versão exibida")
  void filtra_por_versao() {
    var wanted = SurveyVersionId.generate();
    var other = SurveyVersionId.generate();
    var mine =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(wanted)
            .openedAt(SECOND)
            .buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(other)
        .openedAt(FIRST)
        .buildSavedIn(displays);

    var output = useCase.execute(builder().forVersion(wanted).build());

    assertThat(output.items()).extracting(DisplaySummaryOutput::id).containsExactly(mine.id());
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Filtra por desfecho")
  void filtra_por_desfecho() {
    var dismissed =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .openedAt(SECOND)
            .dismissedAt(SECOND.plusSeconds(5))
            .buildSavedIn(displays);
    anOpenDisplayAt(FIRST);

    var output = useCase.execute(builder().withOutcome(DisplayOutcome.DISMISSED).build());

    assertThat(output.items()).extracting(DisplaySummaryOutput::id).containsExactly(dismissed.id());
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("O período é inclusivo nos dois extremos")
  void periodo_inclusivo_nos_extremos() {
    var first = anOpenDisplayAt(FIRST);
    var second = anOpenDisplayAt(SECOND);
    var third = anOpenDisplayAt(THIRD);

    var output = useCase.execute(builder().openedFrom(FIRST).openedTo(THIRD).build());

    assertThat(output.items())
        .extracting(DisplaySummaryOutput::id)
        .containsExactly(third, second, first);

    var middle = useCase.execute(builder().openedFrom(SECOND).openedTo(SECOND).build());

    assertThat(middle.items()).extracting(DisplaySummaryOutput::id).containsExactly(second);
    assertThat(middle.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Filtros combinados restringem em conjunto")
  void filtros_combinados() {
    var versionId = SurveyVersionId.generate();
    var wanted =
        SurveyDisplayFactory.aDisplay()
            .forApplication(applicationId)
            .forSurvey(surveyId)
            .forVersion(versionId)
            .openedAt(SECOND)
            .completedAt(SECOND.plusSeconds(5))
            .buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .forVersion(versionId)
        .openedAt(THIRD)
        .dismissedAt(THIRD.plusSeconds(5))
        .buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay()
        .forApplication(applicationId)
        .forSurvey(surveyId)
        .openedAt(SECOND)
        .completedAt(SECOND.plusSeconds(5))
        .buildSavedIn(displays);

    var output =
        useCase.execute(
            builder()
                .forVersion(versionId)
                .withOutcome(DisplayOutcome.COMPLETED)
                .openedFrom(FIRST)
                .openedTo(SECOND)
                .build());

    assertThat(output.items()).extracting(DisplaySummaryOutput::id).containsExactly(wanted.id());
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Página além do fim devolve vazio com o total correto, nunca erro")
  void pagina_alem_do_fim() {
    anOpenDisplayAt(FIRST);
    anOpenDisplayAt(SECOND);

    var output = useCase.execute(builder().page(9, 2).build());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isEqualTo(2);
    assertThat(output.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("Não devolve exibição de outra aplicação, mesmo na mesma pesquisa")
  void isola_as_aplicacoes() {
    var mine = anOpenDisplayAt(SECOND);
    SurveyDisplayFactory.aDisplay()
        .forApplication(ApplicationId.generate())
        .forSurvey(surveyId)
        .openedAt(THIRD)
        .buildSavedIn(displays);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(DisplaySummaryOutput::id).containsExactly(mine);
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Pesquisa inexistente, de outra aplicação ou malformada recusa igual")
  void recusa_pesquisa_fora_do_escopo() {
    var alheia = surveys.aSurveyIn(ApplicationId.generate());

    assertThatThrownBy(
            () -> useCase.execute(builder().forSurvey(UUID.randomUUID().toString()).build()))
        .isInstanceOf(SurveyNotFoundInApplication.class);
    assertThatThrownBy(() -> useCase.execute(builder().forSurvey(alheia.value()).build()))
        .isInstanceOf(SurveyNotFoundInApplication.class);
    assertThatThrownBy(() -> useCase.execute(builder().forSurvey("nao-e-um-id").build()))
        .isInstanceOf(SurveyNotFoundInApplication.class);
  }
}
