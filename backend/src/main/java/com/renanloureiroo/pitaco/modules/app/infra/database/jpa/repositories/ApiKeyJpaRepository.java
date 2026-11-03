package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApiKeyJpaEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyJpaEntity, String> {

  Optional<ApiKeyJpaEntity> findByIdAndApplicationId(String id, String applicationId);

  // Três derivados em vez de um JPQL com predicado nulo opcional: as assinaturas dizem o que
  // filtram, o JPQL não diria.
  Page<ApiKeyJpaEntity> findByApplicationId(String applicationId, Pageable pageable);

  Page<ApiKeyJpaEntity> findByApplicationIdAndRevokedAtIsNull(
      String applicationId, Pageable pageable);

  Page<ApiKeyJpaEntity> findByApplicationIdAndRevokedAtIsNotNull(
      String applicationId, Pageable pageable);

  Optional<ApiKeyJpaEntity> findBySecretHashAndRevokedAtIsNull(String secretHash);

  // Amortizado: a linha só é tocada quando o registrado é mais antigo que o limiar, de modo que
  // o caminho quente não escreve na mesma linha a cada evento do app hospedeiro (D-17).
  @Modifying(clearAutomatically = true)
  @Query(
      """
      update ApiKeyJpaEntity k
         set k.lastUsedAt = :now
       where k.id = :id
         and (k.lastUsedAt is null or k.lastUsedAt < :threshold)
      """)
  int touch(
      @Param("id") String id, @Param("now") Instant now, @Param("threshold") Instant threshold);

  // Condicional em revoked_at is null: é o que resolve duas revogações simultâneas sem lock.
  // clearAutomatically porque quem perde a corrida relê dentro da mesma transação, e sem
  // limpar o contexto a releitura devolveria a instância já carregada, anterior ao update.
  @Modifying(clearAutomatically = true)
  @Query(
      """
      update ApiKeyJpaEntity k
         set k.revokedAt = :revokedAt
       where k.id = :id
         and k.revokedAt is null
      """)
  int revoke(@Param("id") String id, @Param("revokedAt") Instant revokedAt);
}
