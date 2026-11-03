package com.renanloureiroo.pitaco.modules.collect.domain.health;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Resumo, não identidade: a supressão não cria respondente, e guardar a referência crua ao lado
// dela seria um segundo cadastro de quem nunca viu pesquisa nenhuma. O resumo só serve para
// reconhecer o mesmo dispositivo repetindo a mesma supressão.
public record SuppressionDedupKey(String value) {

  public static SuppressionDedupKey of(ApplicationId applicationId, RespondentIdentity identity) {
    var material = applicationId.value() + "|" + identity.kind().name() + "|" + identity.value();
    return new SuppressionDedupKey(sha256(material));
  }

  private static String sha256(String material) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", impossible);
    }
  }
}
