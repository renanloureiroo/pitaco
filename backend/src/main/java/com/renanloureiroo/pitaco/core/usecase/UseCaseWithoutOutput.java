package com.renanloureiroo.pitaco.core.usecase;

/**
 * Contrato de um caso de uso que recebe input e não retorna resultado.
 *
 * @param <I> tipo do input do caso de uso
 */
@FunctionalInterface
public interface UseCaseWithoutOutput<I> {

    void execute(I input);
}
