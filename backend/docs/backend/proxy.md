# Contrato do proxy

Para quem aponta a `baseUrl` do SDK para o próprio gateway em vez de falar direto com o Pitaco
(ADR-0008). O SDK é idêntico nas duas topologias: não sabe se fala com o Pitaco ou com um
proxy, não tem modo e não ramifica. Isso só funciona se o gateway for **transparente**.

O Pitaco não constrói gateway nenhum. O que ele assume é este documento.

## O que o gateway oferece, e o que não oferece

**Oferece** o que um ponto de passagem oferece: controle de endereço, observabilidade e política
de rede do lado do app hospedeiro.

**Não oferece:**

- **Não protege a chave.** A chave continua saindo do SDK, no bundle do app, e continua podendo
  ser extraída.
- **Não torna a identidade do respondente confiável.** Ela também sai do SDK e continua forjável.
  A defesa é o limite de requisições.
- **Não é chamador privilegiado.** Não existe credencial de servidor. Nenhum intermediário
  sobrescreve o que o cliente enviou.

## Rotas a repassar

Só a superfície pública. Todas são `POST`, com corpo JSON:

| Rota no gateway | Rota no Pitaco |
| --- | --- |
| `{prefixo}/collect/eligibility` | `/api/collect/eligibility` |
| `{prefixo}/collect/displays` | `/api/collect/displays` |
| `{prefixo}/collect/displays/{displayId}/submission` | `/api/collect/displays/{displayId}/submission` |
| `{prefixo}/collect/suppressions` | `/api/collect/suppressions` |
| `{prefixo}/collect/sdk-errors` | `/api/collect/sdk-errors` |

Na prática: repasse `{prefixo}/collect/*` para `/api/collect/*` com o mesmo método e o mesmo
restante do caminho.

**Não repasse mais nada.** Em especial, nunca `/api/applications/**`, que é a superfície
administrativa do painel, nem `/api/actuator/**` ou a documentação da API.

## Cabeçalhos

Repassar **sem alterar**:

| Cabeçalho | Por quê |
| --- | --- |
| `X-Pitaco-Key` | Identifica a aplicação. Sem ele, `401`. |
| `X-Pitaco-Sdk-Version` | Alimenta a distribuição de versões e o aviso de compatibilidade. |
| `Content-Type` | Todo corpo é `application/json`. |

Definir no caminho:

| Cabeçalho | Valor |
| --- | --- |
| `X-Forwarded-For` | O IP do cliente como o gateway o viu, substituindo o que veio do cliente. |
| `X-Forwarded-Proto` | O esquema que o cliente usou. |
| `X-Forwarded-Host` | O host que o cliente usou. |

### Como o Pitaco decide a origem

O limite por origem nunca usa um valor que o cliente escolhe. Só vale o que um **proxy
confiável** escreveu, e a lista de proxies confiáveis é configuração do Pitaco
(`pitaco.collect.rate-limit.origin.trusted-proxies`, em CIDR). O padrão é loopback mais as faixas
privadas, que cobrem o cloudflared na rede do Docker.

1. Quem conecta direto na API, sem ser proxy confiável, é a própria origem. Nenhum cabeçalho é
   lido.
2. Atrás de proxy confiável, vale o cabeçalho `CF-Connecting-IP`
   (`origin.client-ip-header`), que a Cloudflare preenche com o IP de quem chegou até ela.
3. Sem esse cabeçalho, o `X-Forwarded-For` é lido **do fim para o começo**, pulando os proxies
   confiáveis. O primeiro endereço que não é confiável é a origem. O começo da lista, que o
   cliente escreve, só é alcançado se todos depois dele forem confiáveis.

Contar um número fixo de saltos foi descartado: quando falta um salto no caminho, a contagem cai
num valor escrito pelo cliente.

### O que isso significa para o gateway

Com o túnel da Cloudflare, o IP que chega em `CF-Connecting-IP` é o do **gateway**, não o do
usuário do app. Por padrão, todo o tráfego que passa pelo gateway conta como **uma origem só**, e
o limite de 120 por minuto por origem vale para o app inteiro.

Para contar por usuário, declare o IP de saída do gateway em `trusted-proxies`, com `/32`. O
Pitaco então pula o gateway e usa o IP que ele escreveu no `X-Forwarded-For`. O gateway precisa
**substituir** o cabeçalho que veio do cliente pelo IP que ele próprio viu, e não acrescentar a
ele: acrescentar deixaria o valor anterior nas mãos do cliente.

O limite por chave vale nas duas situações e é o que contém uma chave extraída do bundle.

### Risco residual

- Um proxy declarado confiável pode escrever qualquer origem. Declare só o que você controla.
- Se a API for exposta sem o túnel, o par imediato passa a ser o cliente real e a origem continua
  correta. O que se perde é só o `CF-Connecting-IP`, que deixa de ser lido porque o par não é
  confiável.

## O que não pode mudar no caminho

- **O corpo.** Nem reescrito, nem recomprimido com outro tipo, nem truncado. Os maiores ficam bem
  abaixo de 64 KB.
- **A chave.** Nunca trocada por outra chave "do servidor".
- **A identidade do respondente**, em `respondent`, `respondentReference` e `deviceId`.
- **O `displayId` no caminho da submissão.** É a chave de idempotência gerada no dispositivo.

## Respostas

Repassar **intactas**, com status, corpo e cabeçalhos:

| Status | Rota | Significado |
| --- | --- | --- |
| 200 | elegibilidade | Com pesquisa ou com `survey: null`. Nunca vire isto em `204`. |
| 200, 201 | abertura | Abertura repetida ou nova. |
| 204 | submissão | Gravado, ou reenvio idêntico. |
| 202 | supressões, relatórios de erro | Recebido. |
| 400, 401, 404, 409, 422 | todas | `application/problem+json` com `code`. O SDK decide por eles. |
| 429 | todas | Com `Retry-After`. **Não descarte o cabeçalho.** |

Não aplique cache: toda resposta é por respondente, e todas as rotas são `POST`.

Não reescreva erro do Pitaco para uma página HTML do gateway. O SDK espera JSON.

## Timeouts

| Etapa | Recomendado |
| --- | --- |
| Conexão com o Pitaco | 5 s |
| Leitura da resposta | 10 s |

O SDK abandona a consulta de elegibilidade bem antes disso. O timeout do gateway existe para não
segurar conexão, não para esperar o Pitaco por mais tempo que o app.

## Exemplos

Os exemplos assumem o Pitaco em `https://pitaco.exemplo.com` e o prefixo `/pitaco` no gateway.
O SDK é configurado com `baseUrl: "https://gateway.exemplo.com/pitaco"`.

### Caddy

```caddyfile
gateway.exemplo.com {
	handle_path /pitaco/collect/* {
		rewrite * /api/collect{uri}
		reverse_proxy https://pitaco.exemplo.com {
			header_up Host pitaco.exemplo.com
			transport http {
				dial_timeout 5s
				response_header_timeout 10s
			}
		}
	}

	handle /pitaco/* {
		respond 404
	}
}
```

O Caddy, desde a 2.5, ignora o `X-Forwarded-For` que vem de cliente não confiável e o define com
o IP que ele viu. É exatamente o comportamento pedido acima, sem configuração extra.

### Nginx

```nginx
location /pitaco/collect/ {
    limit_except POST { deny all; }

    proxy_pass https://pitaco.exemplo.com/api/collect/;
    proxy_set_header Host pitaco.exemplo.com;
    proxy_ssl_server_name on;

    # Substitui, não acrescenta: $remote_addr é o IP que o Nginx viu.
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;

    proxy_connect_timeout 5s;
    proxy_read_timeout 10s;
    proxy_intercept_errors off;
    proxy_buffering off;
    client_max_body_size 64k;
}

location /pitaco/ {
    return 404;
}
```

`X-Pitaco-Key`, `X-Pitaco-Sdk-Version` e `Content-Type` passam sem configuração: o Nginx repassa
os cabeçalhos do cliente por padrão.

## Conferir

Com o gateway de pé, a mesma chamada precisa dar o mesmo resultado nos dois caminhos:

```bash
curl -si https://gateway.exemplo.com/pitaco/collect/eligibility \
  -H "X-Pitaco-Key: $CHAVE" \
  -H "X-Pitaco-Sdk-Version: 1.0.0" \
  -H "Content-Type: application/json" \
  -d '{"event":"teste.proxy","respondent":{"deviceId":"0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11"}}'
```

A resposta esperada é `200` com `{"survey":null}` quando nenhuma pesquisa escuta `teste.proxy`.
Em seguida, o evento aparece na lista de eventos observados da aplicação no painel, o que prova
que a chave chegou intacta.

Sem a chave, a resposta precisa ser o `401` do Pitaco, em JSON, e não um erro do gateway.
