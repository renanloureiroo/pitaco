package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryAnswerRepository implements AnswerRepository {

  private final Map<AnswerId, Answer> answers = new LinkedHashMap<>();

  private int saveAllCalls;

  @Override
  public List<Answer> findByDisplay(DisplayId displayId) {
    return answers.values().stream()
        .filter(answer -> answer.getDisplayId().equals(displayId))
        .map(InMemoryAnswerRepository::copyOf)
        .toList();
  }

  @Override
  public void saveAll(List<Answer> toSave) {
    saveAllCalls++;
    toSave.forEach(answer -> answers.put(answer.id(), copyOf(answer)));
  }

  public int saveAllCalls() {
    return saveAllCalls;
  }

  public List<Answer> findAll() {
    return List.copyOf(answers.values());
  }

  public boolean isEmpty() {
    return answers.isEmpty();
  }

  private static Answer copyOf(Answer answer) {
    return Answer.restore(
        answer.id(),
        answer.getDisplayId(),
        answer.getQuestionKey(),
        answer.getStatus(),
        answer.value(),
        answer.getAnsweredAt());
  }
}
