package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationAttributeJpaEntity;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// O instantâneo inteiro viaja como um objeto JSON e é desmontado no banco: dois comandos por
// consulta, quantos atributos vierem, em vez de dois por atributo. Os dois amortizam a escrita
// como o catálogo de eventos — a linha só é tocada quando o registrado é mais antigo que o
// limiar.
public interface ApplicationAttributeJpaRepository
    extends JpaRepository<ApplicationAttributeJpaEntity, String> {

  Page<ApplicationAttributeJpaEntity> findByApplicationId(String applicationId, Pageable pageable);

  @Modifying(clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          insert into application_attributes (id, application_id, name, first_seen_at, last_seen_at)
          select cast(gen_random_uuid() as varchar), :applicationId, attribute.key, :now, :now
            from jsonb_each_text(cast(:attributes as jsonb)) as attribute
          on conflict (application_id, name) do update
             set last_seen_at = excluded.last_seen_at
           where application_attributes.last_seen_at < :threshold
          """)
  int recordNames(
      @Param("applicationId") String applicationId,
      @Param("attributes") String attributes,
      @Param("now") Instant now,
      @Param("threshold") Instant threshold);

  // Valor já conhecido sempre avança; valor novo só entra enquanto o atributo está abaixo do
  // limite de valores distintos.
  @Modifying(clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          insert into application_attribute_values (id, attribute_id, value, last_seen_at)
          select cast(gen_random_uuid() as varchar), known.id, attribute.value, :now
            from jsonb_each_text(cast(:attributes as jsonb)) as attribute
            join application_attributes known
              on known.application_id = :applicationId and known.name = attribute.key
           where exists (select 1 from application_attribute_values seen
                          where seen.attribute_id = known.id and seen.value = attribute.value)
              or (select count(*) from application_attribute_values seen
                   where seen.attribute_id = known.id) < :maxValues
          on conflict (attribute_id, value) do update
             set last_seen_at = excluded.last_seen_at
           where application_attribute_values.last_seen_at < :threshold
          """)
  int recordValues(
      @Param("applicationId") String applicationId,
      @Param("attributes") String attributes,
      @Param("now") Instant now,
      @Param("threshold") Instant threshold,
      @Param("maxValues") int maxValues);
}
