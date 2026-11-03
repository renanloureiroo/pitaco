package com.renanloureiroo.pitaco.modules.privacy.domain.retention;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

// Uma contagem aditiva: quantas respostas da pergunta tinham aquele status, marcaram aquela
// opção ou deram aquele número. É tudo que os agregados de resultado precisam para somar.
public record SnapshotCount(QuestionKey key, Dimension dimension, String value, long count) {

  private static final String COUNT_INVALID_CODE = "aggregate_snapshot.count_invalid";

  public enum Dimension {
    STATUS,
    OPTION,
    NUMBER
  }

  public SnapshotCount {
    if (count <= 0) {
      throw new DomainException(
          ErrorType.VALIDATION, COUNT_INVALID_CODE, "Contagem congelada precisa ser positiva");
    }
  }
}
