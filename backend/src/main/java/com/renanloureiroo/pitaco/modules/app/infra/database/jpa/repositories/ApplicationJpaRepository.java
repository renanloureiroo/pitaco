package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationJpaRepository extends JpaRepository<ApplicationJpaEntity, String> {

  Optional<ApplicationJpaEntity> findBySlug(String slug);

  // Derivado em vez de JPQL com predicado nulo opcional: a assinatura diz o que filtra. Sem
  // filtro a listagem usa o findAll(Pageable) herdado, que o Pageable recorta.
  Page<ApplicationJpaEntity> findByStatus(Status status, Pageable pageable);
}
