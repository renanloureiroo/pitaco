package com.renanloureiroo.pitaco.testsupport.transaction;

import com.renanloureiroo.pitaco.core.transaction.Transactor;
import java.util.function.Supplier;

public class DirectTransactor implements Transactor {

  private int executions;

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    executions++;
    return work.get();
  }

  public int executions() {
    return executions;
  }
}
