package com.renanloureiroo.pitaco.core.usecase;

/**
 * Contrato base de um caso de uso com entrada e saída.
 *
 * @param <I> tipo do input do caso de uso
 * @param <O> tipo do output do caso de uso
 */
@FunctionalInterface
public interface UseCase<I, O> {

  O execute(I input);
}
