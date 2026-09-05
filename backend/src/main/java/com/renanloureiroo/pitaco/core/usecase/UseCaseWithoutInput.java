package com.renanloureiroo.pitaco.core.usecase;

/**
 * Contrato de um caso de uso que não recebe input e retorna um resultado.
 *
 * @param <O> tipo do output do caso de uso
 */
@FunctionalInterface
public interface UseCaseWithoutInput<O> {

    O execute();
}
