package com.renanloureiroo.pitaco.core.error;

/**
 * Natureza de um erro do core, independente de protocolo.
 *
 * <p>A tradução para um protocolo específico (status HTTP, código gRPC, etc.) é responsabilidade do
 * adapter, nunca do core.
 */
public enum ErrorType {

  /** Recurso solicitado não existe. */
  NOT_FOUND,

  /** Estado atual do recurso impede a operação (duplicidade, concorrência). */
  CONFLICT,

  /** Dados de entrada malformados ou fora do formato esperado. */
  VALIDATION,

  /** Identidade do solicitante ausente ou não comprovada. */
  UNAUTHORIZED,

  /** Identidade conhecida, porém sem permissão para a operação. */
  FORBIDDEN,

  /** Invariante de domínio ou regra de negócio violada. */
  BUSINESS_RULE
}
