package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers.SurveyVersionJpaMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyVersionRepositoryJpa implements SurveyVersionRepository {

  private final SurveyVersionJpaRepository repository;

  public SurveyVersionRepositoryJpa(SurveyVersionJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public SurveyVersion create(SurveyVersion version) {
    return save(version);
  }

  @Override
  public Optional<SurveyVersion> findDraft(SurveyId surveyId) {
    return repository.findDraft(surveyId.value()).map(SurveyVersionJpaMapper::toDomain);
  }

  @Override
  public Optional<SurveyVersion> findByNumber(SurveyId surveyId, int number) {
    return repository.findByNumber(surveyId.value(), number).map(SurveyVersionJpaMapper::toDomain);
  }

  @Override
  public Optional<SurveyVersion> findPublished(SurveyId surveyId) {
    return findAllPublished(surveyId).stream().findFirst();
  }

  @Override
  public Page<SurveyVersion> findPublishedPage(ListSurveyVersionsQuery query) {
    // O recorte é aplicado sobre a lista já ordenada por número: o join fetch das coleções e o
    // limit do banco não convivem, e a quantidade de versões de uma pesquisa é pequena.
    var published = findAllPublished(query.surveyId());

    var items = published.stream().skip(query.offset()).limit(Math.max(query.size(), 0)).toList();

    return new Page<>(items, published.size());
  }

  @Override
  public List<SurveyVersion> findAllPublished(SurveyId surveyId) {
    return repository.findAllPublished(surveyId.value()).stream()
        .map(SurveyVersionJpaMapper::toDomain)
        .toList();
  }

  @Override
  public Map<SurveyId, TriggerWindow> findPublishedWindows(List<SurveyId> surveyIds) {
    if (surveyIds.isEmpty()) {
      return Map.of();
    }

    var windows = new LinkedHashMap<SurveyId, TriggerWindow>();
    repository
        .findPublishedWindows(surveyIds.stream().map(SurveyId::value).toList())
        .forEach(
            row ->
                windows.put(
                    SurveyId.of((String) row[0]),
                    new TriggerWindow((Instant) row[1], Optional.ofNullable((Instant) row[2]))));

    return Map.copyOf(windows);
  }

  @Override
  public SurveyVersion update(SurveyVersion version) {
    return save(version);
  }

  @Override
  public void delete(SurveyVersionId id) {
    repository.deleteById(id.value());
  }

  @Override
  public void deleteBySurveyId(SurveyId surveyId) {
    repository.deleteBySurveyId(surveyId.value());
  }

  // A versão inteira vai junto: perguntas, opções e regras entram pelo cascade, em um save só.
  private SurveyVersion save(SurveyVersion version) {
    repository.save(SurveyVersionJpaMapper.toJpa(version));
    return version;
  }
}
