package com.renanloureiroo.pitaco.core.usecase;

/**
 * Contrato de um caso de uso que não recebe input nem retorna resultado.
 */
@FunctionalInterface
public interface UseCaseWithoutInputAndOutput {

    void execute();
}
