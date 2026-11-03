package com.renanloureiroo.pitaco.infra.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.ConflictException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@DisplayName("SpringTransactor sut")
class SpringTransactorTest {

  private RecordingTransactionManager transactionManager;
  private SpringTransactor sut;

  @BeforeEach
  void setUp() {
    transactionManager = new RecordingTransactionManager();
    sut = new SpringTransactor(transactionManager);
  }

  @Test
  @DisplayName("Deve commitar e devolver o resultado quando o trabalho conclui")
  void deve_commitar_quando_o_trabalho_conclui() {
    var result = sut.inTransaction(() -> "ok");

    assertThat(result).isEqualTo("ok");
    assertThat(transactionManager.events).containsExactly("begin", "commit");
  }

  @Test
  @DisplayName("Deve fazer rollback e preservar o tipo do erro quando o trabalho falha")
  void deve_fazer_rollback_preservando_o_erro() {
    var failure = new ConflictException("app.duplicated", "Já existe");

    assertThatThrownBy(
            () ->
                sut.inTransaction(
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    assertThat(transactionManager.events).containsExactly("begin", "rollback");
  }

  @Test
  @DisplayName("Deve executar trabalho sem retorno dentro da transação")
  void deve_executar_trabalho_sem_retorno() {
    var executed = new ArrayList<String>();

    sut.runInTransaction(() -> executed.add("escrita"));

    assertThat(executed).containsExactly("escrita");
    assertThat(transactionManager.events).containsExactly("begin", "commit");
  }

  private static final class RecordingTransactionManager implements PlatformTransactionManager {

    private final List<String> events = new ArrayList<>();

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      events.add("begin");
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      events.add("commit");
    }

    @Override
    public void rollback(TransactionStatus status) {
      events.add("rollback");
    }
  }
}
