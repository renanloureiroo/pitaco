package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOptionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionOptionDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ScaleRangeDTO;
import java.util.List;
import java.util.Locale;

public final class QuestionPresenter {

  private QuestionPresenter() {}

  public static QuestionResponseDTO present(QuestionOutput output) {
    return new QuestionResponseDTO(
        output.id(),
        output.key(),
        output.statement(),
        output.type().name().toLowerCase(Locale.ROOT),
        output.position(),
        output.required(),
        output.options().stream().map(QuestionPresenter::optionOf).toList(),
        output.range().map(range -> new ScaleRangeDTO(range.min(), range.max())).orElse(null));
  }

  public static List<QuestionResponseDTO> presentAll(List<QuestionOutput> outputs) {
    return outputs.stream().map(QuestionPresenter::present).toList();
  }

  private static QuestionOptionDTO optionOf(QuestionOptionOutput option) {
    return new QuestionOptionDTO(option.label(), option.value());
  }
}
