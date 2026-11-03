package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// SHA-256 e não um KDF: o segredo tem 256 bits de SecureRandom, então não há dicionário a
// resistir, e o custo de um KDF viraria latência em cada verificação.
public record ApiKeySecret(String prefix, String hash) {

  private static final String MARKER = "pit_";
  private static final int PREFIX_BYTES = 4;
  private static final int SECRET_BYTES = 32;
  private static final String DIGEST = "SHA-256";

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final HexFormat HEX = HexFormat.of();

  public record Generated(ApiKeySecret secret, String plainText) {}

  public static Generated generate() {
    var prefix = MARKER + HEX.formatHex(randomBytes(PREFIX_BYTES));
    var plainText = prefix + "_" + ENCODER.encodeToString(randomBytes(SECRET_BYTES));

    return new Generated(new ApiKeySecret(prefix, hashOf(plainText)), plainText);
  }

  public static String hashOf(String plainText) {
    try {
      var digest = MessageDigest.getInstance(DIGEST);
      return HEX.formatHex(digest.digest(plainText.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unsupported) {
      throw new IllegalStateException(DIGEST + " é exigido de toda JVM", unsupported);
    }
  }

  private static byte[] randomBytes(int length) {
    var bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  @Override
  public String toString() {
    return prefix;
  }
}
