package com.renanloureiroo.pitaco.infra.http.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OriginResolver")
class OriginResolverTest {

  private static final List<String> PRIVATE_RANGES =
      List.of(
          "127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7");

  private final OriginResolver resolver = OriginResolver.of("CF-Connecting-IP", PRIVATE_RANGES);

  private static Map<String, String> headers(String... pairs) {
    var map = new HashMap<String, String>();
    for (var i = 0; i < pairs.length; i += 2) {
      map.put(pairs[i], pairs[i + 1]);
    }
    return map;
  }

  private String resolve(OriginResolver target, String peer, Map<String, String> headers) {
    return target.resolve(peer, headers::get);
  }

  @Nested
  @DisplayName("Conexão direta, sem proxy confiável no caminho")
  class Direta {

    @Test
    @DisplayName("Quem conecta direto é a origem, e nenhum cabeçalho forjado muda isso")
    void ignora_cabecalhos_do_cliente() {
      var forged =
          headers("X-Forwarded-For", "198.51.100.1, 198.51.100.2", "CF-Connecting-IP", "198.51.100.3");

      assertThat(resolve(resolver, "203.0.113.10", forged)).isEqualTo("203.0.113.10");
    }
  }

  @Nested
  @DisplayName("Atrás do túnel, com o par imediato na rede privada")
  class AtrasDoTunel {

    @Test
    @DisplayName("O cabeçalho do IP do cliente vence, quando presente")
    void usa_o_cabecalho_do_cliente() {
      var chain = headers("CF-Connecting-IP", "203.0.113.7", "X-Forwarded-For", "198.51.100.1, 203.0.113.7");

      assertThat(resolve(resolver, "172.18.0.5", chain)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Sem o cabeçalho, forjar o começo do X-Forwarded-For não escolhe a origem")
    void primeiro_valor_forjado_nao_conta() {
      for (var forged : List.of("1.2.3.4", "198.51.100.9", "8.8.8.8")) {
        var chain = headers("X-Forwarded-For", forged + ", 203.0.113.7");

        assertThat(resolve(resolver, "172.18.0.5", chain)).isEqualTo("203.0.113.7");
      }
    }

    @Test
    @DisplayName("Proxies confiáveis no fim da cadeia são pulados")
    void pula_os_confiaveis_do_fim() {
      var chain = headers("X-Forwarded-For", "1.2.3.4, 203.0.113.7, 10.0.0.4, 192.168.1.2");

      assertThat(resolve(resolver, "127.0.0.1", chain)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Gateway declarado confiável: a origem é o cliente que ele informou")
    void gateway_confiavel_repassa_o_cliente() {
      var withGateway =
          OriginResolver.of(
              "CF-Connecting-IP",
              List.of("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "198.51.100.10/32"));
      var chain =
          headers(
              "CF-Connecting-IP", "198.51.100.10", "X-Forwarded-For", "203.0.113.7, 198.51.100.10");

      assertThat(resolve(withGateway, "172.18.0.5", chain)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Gateway não declarado: todo o tráfego dele conta como uma origem só")
    void gateway_nao_declarado_e_a_origem() {
      var chain =
          headers(
              "CF-Connecting-IP", "198.51.100.10", "X-Forwarded-For", "203.0.113.7, 198.51.100.10");

      assertThat(resolve(resolver, "172.18.0.5", chain)).isEqualTo("198.51.100.10");
    }

    @Test
    @DisplayName("Salto ilegível na cadeia encerra a leitura e a origem vira o par imediato")
    void salto_ilegivel_para_no_par() {
      var chain = headers("X-Forwarded-For", "203.0.113.7, lixo, 10.0.0.4");

      assertThat(resolve(resolver, "172.18.0.5", chain)).isEqualTo("172.18.0.5");
    }

    @Test
    @DisplayName("Cadeia só de confiáveis, ou ausente, cai no par imediato")
    void so_confiaveis_cai_no_par() {
      assertThat(resolve(resolver, "127.0.0.1", headers())).isEqualTo("127.0.0.1");
      assertThat(resolve(resolver, "127.0.0.1", headers("X-Forwarded-For", "10.0.0.1")))
          .isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Cabeçalho desligado na configuração não é lido")
    void cabecalho_desligado() {
      var withoutHeader = OriginResolver.of(" ", PRIVATE_RANGES);
      var chain = headers("CF-Connecting-IP", "198.51.100.3", "X-Forwarded-For", "203.0.113.7");

      assertThat(resolve(withoutHeader, "172.18.0.5", chain)).isEqualTo("203.0.113.7");
    }
  }

  @Nested
  @DisplayName("Forma dos endereços")
  class Forma {

    @Test
    @DisplayName("Porta e colchetes saem, e a mesma origem escrita de dois jeitos conta junto")
    void normaliza() {
      assertThat(resolve(resolver, "127.0.0.1", headers("X-Forwarded-For", "203.0.113.7:4431")))
          .isEqualTo("203.0.113.7");
      assertThat(
              resolve(resolver, "127.0.0.1", headers("X-Forwarded-For", "[2001:db8::1]:443")))
          .isEqualTo(resolve(resolver, "127.0.0.1", headers("X-Forwarded-For", "2001:DB8:0::1")));
    }

    @Test
    @DisplayName("Octeto acima de 255 e nome de host não são endereços, e não viram consulta DNS")
    void recusa_o_que_nao_e_literal() {
      assertThat(IpLiteral.parse("999.1.1.1")).isEmpty();
      assertThat(IpLiteral.parse("example.com")).isEmpty();
      assertThat(IpLiteral.parse("gggg::1")).isEmpty();
      assertThat(IpLiteral.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("Par imediato ilegível é usado como veio")
    void par_ilegivel() {
      assertThat(resolve(resolver, "unix-socket", headers())).isEqualTo("unix-socket");
    }
  }

  @Nested
  @DisplayName("Faixas CIDR")
  class Faixas {

    @Test
    @DisplayName("Contém o que está no prefixo, inclusive fora do limite de byte")
    void contem() {
      var range = IpRange.parse("172.16.0.0/12");

      assertThat(range.contains(IpLiteral.parse("172.31.255.1").orElseThrow())).isTrue();
      assertThat(range.contains(IpLiteral.parse("172.32.0.1").orElseThrow())).isFalse();
      assertThat(IpRange.parse("fc00::/7").contains(IpLiteral.parse("fd12::1").orElseThrow()))
          .isTrue();
    }

    @Test
    @DisplayName("IPv4 nunca cai numa faixa IPv6, nem o contrário")
    void familias_nao_se_misturam() {
      assertThat(IpRange.parse("::1/128").contains(IpLiteral.parse("127.0.0.1").orElseThrow()))
          .isFalse();
    }

    @Test
    @DisplayName("Faixa malformada falha na subida")
    void malformada() {
      assertThatThrownBy(() -> IpRange.parse("10.0.0.0"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> IpRange.parse("10.0.0.0/33"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> IpRange.parse("rede/8"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> OriginResolver.of("CF-Connecting-IP", List.of("10.0.0.0/x")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
