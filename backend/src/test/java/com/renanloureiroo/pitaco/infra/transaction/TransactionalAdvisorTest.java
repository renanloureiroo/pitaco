package com.renanloureiroo.pitaco.infra.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.testsupport.transaction.DirectTransactor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.ProxyFactory;

@DisplayName("TransactionalAdvisor")
class TransactionalAdvisorTest {

  interface Work {
    void execute();

    void untouched();
  }

  @Transactional
  static class AnnotatedClass implements Work {
    @Override
    public void execute() {}

    @Override
    public void untouched() {}
  }

  static class AnnotatedMethod implements Work {
    @Override
    @Transactional
    public void execute() {}

    @Override
    public void untouched() {}
  }

  static class NotAnnotated implements Work {
    @Override
    public void execute() {}

    @Override
    public void untouched() {}
  }

  private final DirectTransactor transactor = new DirectTransactor();
  private final TransactionalAdvisor advisor = new TransactionalAdvisor(transactor);

  private boolean matches(Class<?> type, String methodName) {
    Pointcut pointcut = advisor.getPointcut();
    Method method = methodOf(type, methodName);

    return pointcut.getClassFilter().matches(type)
        && pointcut.getMethodMatcher().matches(method, type);
  }

  private static Method methodOf(Class<?> type, String name) {
    try {
      return type.getDeclaredMethod(name);
    } catch (NoSuchMethodException impossible) {
      throw new AssertionError(impossible);
    }
  }

  @Test
  @DisplayName("Anotado na classe: todo método entra")
  void aceita_a_anotacao_na_classe() {
    assertThat(matches(AnnotatedClass.class, "execute")).isTrue();
    assertThat(matches(AnnotatedClass.class, "untouched")).isTrue();
  }

  @Test
  @DisplayName("Anotado no método: só aquele método entra")
  void aceita_a_anotacao_no_metodo() {
    assertThat(matches(AnnotatedMethod.class, "execute")).isTrue();
    assertThat(matches(AnnotatedMethod.class, "untouched")).isFalse();
  }

  @Test
  @DisplayName("Sem anotação, nenhum método entra")
  void ignora_quem_nao_declarou() {
    assertThat(matches(NotAnnotated.class, "execute")).isFalse();
    assertThat(matches(NotAnnotated.class, "untouched")).isFalse();
  }

  @Test
  @DisplayName("O método anotado roda dentro da transação; o vizinho, fora")
  void abre_transacao_apenas_no_metodo_anotado() {
    var proxy = proxyOf(new AnnotatedMethod());

    proxy.untouched();
    assertThat(transactor.executions()).isZero();

    proxy.execute();
    assertThat(transactor.executions()).isEqualTo(1);
  }

  private Work proxyOf(Work target) {
    var factory = new ProxyFactory(target);
    factory.addAdvisor(advisor);
    return (Work) factory.getProxy();
  }
}
