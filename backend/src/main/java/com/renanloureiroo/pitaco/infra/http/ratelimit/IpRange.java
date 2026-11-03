package com.renanloureiroo.pitaco.infra.http.ratelimit;

import java.net.InetAddress;

final class IpRange {

  private final byte[] network;
  private final int prefix;

  private IpRange(byte[] network, int prefix) {
    this.network = network;
    this.prefix = prefix;
  }

  // Configuração errada falha na subida, não na primeira requisição.
  static IpRange parse(String cidr) {
    var parts = cidr.strip().split("/", -1);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Faixa de proxy confiável fora do formato CIDR: " + cidr);
    }

    var address =
        IpLiteral.parse(parts[0])
            .orElseThrow(
                () -> new IllegalArgumentException("Endereço inválido na faixa CIDR: " + cidr));
    var bytes = address.getAddress();

    int prefix;
    try {
      prefix = Integer.parseInt(parts[1]);
    } catch (NumberFormatException malformed) {
      throw new IllegalArgumentException("Prefixo inválido na faixa CIDR: " + cidr);
    }
    if (prefix < 0 || prefix > bytes.length * 8) {
      throw new IllegalArgumentException("Prefixo fora do tamanho do endereço: " + cidr);
    }

    return new IpRange(bytes, prefix);
  }

  boolean contains(InetAddress address) {
    var candidate = address.getAddress();
    if (candidate.length != network.length) {
      return false;
    }

    var fullBytes = prefix / 8;
    for (var i = 0; i < fullBytes; i++) {
      if (candidate[i] != network[i]) {
        return false;
      }
    }

    var remainingBits = prefix % 8;
    if (remainingBits == 0) {
      return true;
    }

    var mask = (byte) (0xFF << (8 - remainingBits));
    return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
  }
}
