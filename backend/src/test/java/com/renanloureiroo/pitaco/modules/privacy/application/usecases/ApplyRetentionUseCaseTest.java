package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.SnapshotCount;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPrivacyApplicationGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryAggregateSnapshotRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRetentionRunRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRetentionStore;
import com.renanloureiroo.pitaco.testsupport.transaction.DirectTransactor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApplyRetentionUseCase")
class ApplyRetentionUseCaseTest {

  private static final int BATCH = 2;

  private InMemoryPrivacyApplicationGateway applications;
  private InMemoryRetentionStore store;
  private InMemoryAggregateSnapshotRepository snapshots;
  private InMemoryRetentionRunRepository runs;
  private DirectTransactor transactor;
  private ApplyRetentionUseCase useCase;

  private final SurveyId survey = SurveyId.generate();
  private final SurveyVersionId version = SurveyVersionId.generate();
  private final QuestionKey nps = QuestionKey.generate();
  private final QuestionKey text = QuestionKey.generate();

  @BeforeEach
  void setUp() {
    applications = new InMemoryPrivacyApplicationGateway();
    store = new InMemoryRetentionStore();
    snapshots = new InMemoryAggregateSnapshotRepository();
    runs = new InMemoryRetentionRunRepository();
    transactor = new DirectTransactor();
    useCase = new ApplyRetentionUseCase(applications, store, snapshots, runs, transactor, BATCH);
  }

  private static Instant daysAgo(int days) {
    return Instant.now().minus(Duration.ofDays(days));
  }

  private void npsAnswer(ApplicationId applicationId, String display, int value, Instant answeredAt) {
    store.withAnswer(
        applicationId, survey, version, display, nps, "ANSWERED", Optional.of(value), List.of(),
        Optional.empty(), answeredAt);
  }

  private void textAnswer(ApplicationId applicationId, String display, Instant answeredAt) {
    store.withAnswer(
        applicationId, survey, version, display, text, "ANSWERED", Optional.empty(), List.of(),
        Optional.of("meu telefone é 9999"), answeredAt);
  }

  @Test
  @DisplayName("Sem política configurada, nada é descartado nem registrado")
  void sem_politica() {
    var applicationId = applications.anApplication();
    npsAnswer(applicationId, "d1", 9, daysAgo(400));

    var output = useCase.execute();

    assertThat(output.runs()).isEmpty();
    assertThat(store.findAll()).hasSize(1);
    assertThat(snapshots.findAll()).isEmpty();
    assertThat(runs.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Apaga o vencido em lotes, congela o agregado de cada lote e poupa o recente")
  void apaga_em_lotes() {
    var applicationId = applications.anApplicationRetaining(30, null);
    for (var index = 0; index < 5; index++) {
      npsAnswer(applicationId, "d" + index, 9, daysAgo(40).plusSeconds(index));
    }
    npsAnswer(applicationId, "recente", 3, daysAgo(1));

    var output = useCase.execute();

    assertThat(store.deleteBatches()).containsExactly(2, 2, 1);
    assertThat(store.findAll()).singleElement().satisfies(stored -> assertThat(stored.answer().displayId()).isEqualTo("recente"));
    assertThat(transactor.executions()).isGreaterThanOrEqualTo(4);

    var numberNine =
        snapshots.findAll().stream()
            .flatMap(snapshot -> snapshot.getCounts().stream())
            .filter(count -> count.dimension() == SnapshotCount.Dimension.NUMBER && count.value().equals("9"))
            .mapToLong(SnapshotCount::count)
            .sum();
    assertThat(numberNine).isEqualTo(5);
    assertThat(snapshots.findAll().stream().mapToInt(snapshot -> snapshot.getRespondingDisplays()).sum())
        .isEqualTo(5);

    assertThat(output.runs()).singleElement().satisfies(run -> {
      assertThat(run.answersDeleted()).isEqualTo(5);
      assertThat(run.textsCleared()).isZero();
    });
    assertThat(runs.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("O prazo de texto apaga só o texto: a resposta fica e continua contando como dada")
  void apaga_so_o_texto() {
    var applicationId = applications.anApplicationRetaining(null, 7);
    textAnswer(applicationId, "d1", daysAgo(10));
    textAnswer(applicationId, "d2", daysAgo(2));

    var output = useCase.execute();

    assertThat(store.findAll()).hasSize(2);
    assertThat(store.findAll())
        .filteredOn(stored -> stored.answer().displayId().equals("d1"))
        .singleElement()
        .satisfies(stored -> assertThat(stored.text()).isEmpty());
    assertThat(store.findAll())
        .filteredOn(stored -> stored.answer().displayId().equals("d2"))
        .singleElement()
        .satisfies(stored -> assertThat(stored.text()).isPresent());
    assertThat(snapshots.findAll()).isEmpty();
    assertThat(output.runs()).singleElement().satisfies(run -> assertThat(run.textsCleared()).isEqualTo(1));
  }

  @Test
  @DisplayName("Rodar de novo não apaga nem congela nada, e não registra execução vazia")
  void idempotente() {
    var applicationId = applications.anApplicationRetaining(30, null);
    npsAnswer(applicationId, "d1", 9, daysAgo(40));
    useCase.execute();

    var again = useCase.execute();

    assertThat(again.runs()).isEmpty();
    assertThat(snapshots.findAll()).hasSize(1);
    assertThat(runs.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Uma aplicação não descarta o que é de outra")
  void isola_aplicacoes() {
    var retaining = applications.anApplicationRetaining(30, null);
    var keeping = applications.anApplication();
    npsAnswer(retaining, "d1", 9, daysAgo(40));
    npsAnswer(keeping, "d2", 9, daysAgo(40));

    useCase.execute();

    assertThat(store.findAll()).singleElement().satisfies(stored -> assertThat(stored.applicationId()).isEqualTo(keeping));
  }
}
