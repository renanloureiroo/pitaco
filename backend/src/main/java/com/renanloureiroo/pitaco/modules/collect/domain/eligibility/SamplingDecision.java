package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Função pura do par pesquisa–respondente: reprodutível em qualquer instância, hoje e depois de
// um redeploy, sem uma linha de sorteio gravada no caminho vazio (D-05).
public final class SamplingDecision {

  private static final String DIGEST = "SHA-256";
  private static final int BITS = 63;
  private static final double SCALE = Math.pow(2, BITS);

  private SamplingDecision() {}

  public static boolean accepts(
      SurveyId surveyId, RespondentIdentity identity, SamplingRate rate) {
    return fractionOf(surveyId, identity) < rate.value();
  }

  private static double fractionOf(SurveyId surveyId, RespondentIdentity identity) {
    var digest = digestOf(surveyId.value() + ":" + identity.value());

    var value = 0L;
    for (var index = 0; index < 8; index++) {
      value = (value << 8) | (digest[index] & 0xFFL);
    }

    return (value >>> 1) / SCALE;
  }

  private static byte[] digestOf(String key) {
    try {
      return MessageDigest.getInstance(DIGEST).digest(key.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException unsupported) {
      throw new IllegalStateException(DIGEST + " é exigido de toda JVM", unsupported);
    }
  }
}
