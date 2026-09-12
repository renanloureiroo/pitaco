package com.renanloureiroo.pitaco.infra.http.ratelimit;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

// A origem do limite nunca é um valor que o cliente escolhe. Só se acredita no que um proxy
// confiável escreveu: quem fala direto com a API é a própria origem, e o X-Forwarded-For é lido
// do fim para o começo, pulando os proxies confiáveis. Contar saltos fixos foi descartado: quando
// falta um salto na cadeia, a contagem cai num valor escrito pelo cliente.
public final class OriginResolver {

  static final String FORWARDED_FOR = "X-Forwarded-For";

  private final Optional<String> clientIpHeader;
  private final List<IpRange> trustedProxies;

  private OriginResolver(Optional<String> clientIpHeader, List<IpRange> trustedProxies) {
    this.clientIpHeader = clientIpHeader;
    this.trustedProxies = trustedProxies;
  }

  public static OriginResolver of(String clientIpHeader, List<String> trustedProxies) {
    var header =
        Optional.ofNullable(clientIpHeader).map(String::strip).filter(name -> !name.isEmpty());
    var ranges = trustedProxies == null ? List.<IpRange>of() : trustedProxies.stream().map(IpRange::parse).toList();

    return new OriginResolver(header, ranges);
  }

  public String resolve(String peer, UnaryOperator<String> headers) {
    var peerAddress = IpLiteral.parse(peer);
    if (peerAddress.isEmpty()) {
      return peer == null ? "unknown" : peer;
    }
    if (!trusted(peerAddress.get())) {
      return normalized(peerAddress.get());
    }

    var fromHeader = clientIpHeader.map(headers).flatMap(IpLiteral::parse);
    if (fromHeader.isPresent() && !trusted(fromHeader.get())) {
      return normalized(fromHeader.get());
    }

    var hops = hopsOf(headers.apply(FORWARDED_FOR));
    for (var i = hops.size() - 1; i >= 0; i--) {
      var hop = IpLiteral.parse(hops.get(i));
      if (hop.isEmpty()) {
        break;
      }
      if (!trusted(hop.get())) {
        return normalized(hop.get());
      }
    }

    return normalized(peerAddress.get());
  }

  private boolean trusted(InetAddress address) {
    return trustedProxies.stream().anyMatch(range -> range.contains(address));
  }

  private static List<String> hopsOf(String forwardedFor) {
    if (forwardedFor == null) {
      return List.of();
    }
    return Arrays.stream(forwardedFor.split(",")).map(String::strip).filter(hop -> !hop.isEmpty()).toList();
  }

  private static String normalized(InetAddress address) {
    return address.getHostAddress();
  }
}
