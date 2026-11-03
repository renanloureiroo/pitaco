# Quickstart — validar `006-sdk-app-exemplo`

Superfície em [`contracts/example-surface.md`](./contracts/example-surface.md); proxy em
[`contracts/proxy-gateway.md`](./contracts/proxy-gateway.md).

## Pré-requisitos

- Node 22.18 ou mais novo (os scripts `.ts` rodam com a remoção de tipos nativa)
- Backend do Pitaco na porta 8080:
  `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (sobe o Postgres pelo Docker)
- `cd sdk && npm ci && npm ci --prefix example`
- Simulador iOS ou emulador Android com Expo Go (o `npx expo start --ios|--android` instala)

---

## 1. O portão (inclui tipos e lint do exemplo)

```bash
cd sdk
npm run verify
```

## 2. Bundle sem build nativo

```bash
cd sdk/example
npx expo export -p ios --output-dir dist-ios
npx expo export -p android --output-dir dist-android
```

Os dois precisam terminar com código 0.

## 3. Semear e conferir sem app

```bash
cd sdk/example
npm run seed                                            # simulador iOS
npm run seed -- --target android-emu                    # emulador Android
npm run seed -- --target device --lan-ip 192.168.0.10   # aparelho físico

npm run seed                                            # de novo: nada duplicado

npm run proxy &                                         # em outro terminal
npm run smoke                                           # "Tudo certo."
```

Esperado no smoke, nos dois caminhos: elegibilidade `200` com a pesquisa, abertura `201`, eventos
`202` com `accepted`, resposta `204`, reenvio `204`, elegibilidade com `survey: null` depois de
responder, `401 api_key.missing` sem chave; só no proxy, `404` para rota fora de `/collect`.

## 4. Roteiro no aparelho

```bash
cd sdk/example
npx expo start            # sem CI=1: com ele o Metro não observa os arquivos
```

| # | Passo | Esperado |
| --- | --- | --- |
| 1 | Abrir no Expo Go | a tela "Testar Pesquisas" abre direto, com as cinco pesquisas do seed |
| 2 | "Todos os tipos", "Sheet do SDK", tema claro, "Abrir pesquisa" | bottom sheet do SDK com "Pergunta 1 de 6" |
| 3 | Arrastar a alça para baixo | fecha; Depuração mostra `survey_dismissed · via swipe` |
| 4 | "Resetar Estado", abrir de novo, tocar no fundo | `via backdrop` |
| 5 | "Resetar Estado", forma "Gorhom", abrir, arrastar | sheet do gorhom; `via swipe`; no Android, voltar do sistema → `via hardware_back` |
| 6 | "Resetar Estado", forma "Tela", abrir, seta do cabeçalho | rota empilhada; `via navigation`, um só |
| 7 | "Resetar Estado", qualquer forma, responder até "Enviar" | "Obrigado!", contêiner fecha; `survey_completed`; resposta nos resultados do painel |
| 8 | Tema escuro | pesquisa e moldura no escuro |
| 9 | Depuração → Rede | `POST /collect/eligibility`, `/displays`, `/events`, `/submission` com status; chave truncada |
| 10 | Depuração → Fila e identidade | `deviceId`, fila vazia depois do envio, "Pesquisa já exibida nesta sessão: sim" |
| 11 | "Simular app reaberto" | o estado da sessão volta para "não" |
| 12 | Perfil "Proxy" (com o proxy de pé), abrir | log do proxy com `POST /pitaco/collect/*` |
| 13 | Agrupar eventos por "Exibição" depois de 2, 5 e 6 | as sequências das três formas são iguais |

## 5. Capturas

`docs/capturas/` tem a pergunta de NPS com o 9 marcado nas três formas, em claro e escuro, iOS e
Android (`ios-sheet-claro.png` … `android-tela-escuro.png`), e um vídeo por forma e plataforma
(`ios-sheet.mp4` … `android-tela.mp4`).
