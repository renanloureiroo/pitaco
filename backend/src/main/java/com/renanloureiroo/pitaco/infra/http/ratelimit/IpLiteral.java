package com.renanloureiroo.pitaco.infra.http.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

// Só literais: InetAddress.getByName resolveria nome por DNS, e o valor vem de cabeçalho que o
// cliente escreve. O IPv4 é montado byte a byte; o IPv6 só chega ao getByName com ':' no texto,
// forma que o JDK trata como literal e recusa sem consultar DNS.
final class IpLiteral {

  private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
  private static final Pattern IPV4_WITH_PORT = Pattern.compile("^[\\d.]+:\\d{1,5}$");
  private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  private IpLiteral() {}

  static Optional<InetAddress> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }

    var value = raw.strip();
    if (value.startsWith("[")) {
      var end = value.indexOf(']');
      if (end < 0) {
        return Optional.empty();
      }
      value = value.substring(1, end);
    } else if (IPV4_WITH_PORT.matcher(value).matches()) {
      value = value.substring(0, value.indexOf(':'));
    }

    var ipv4 = IPV4.matcher(value);
    if (ipv4.matches()) {
      return ipv4Of(ipv4.group(1), ipv4.group(2), ipv4.group(3), ipv4.group(4));
    }

    if (value.indexOf(':') >= 0 && IPV6.matcher(value).matches()) {
      try {
        return Optional.of(InetAddress.getByName(value));
      } catch (UnknownHostException invalid) {
        return Optional.empty();
      }
    }

    return Optional.empty();
  }

  private static Optional<InetAddress> ipv4Of(String... octets) {
    var bytes = new byte[4];
    for (var i = 0; i < 4; i++) {
      var octet = Integer.parseInt(octets[i]);
      if (octet > 255) {
        return Optional.empty();
      }
      bytes[i] = (byte) octet;
    }

    try {
      return Optional.of(InetAddress.getByAddress(bytes));
    } catch (UnknownHostException impossible) {
      return Optional.empty();
    }
  }
}
