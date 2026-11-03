package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import java.util.List;

public interface AnswerRepository {

  List<Answer> findByDisplay(DisplayId displayId);

  // Uma escrita em lote, nunca uma por item.
  void saveAll(List<Answer> answers);
}
