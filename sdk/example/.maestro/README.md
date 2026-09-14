# Fluxos Maestro do exemplo

Fluxos de ponta a ponta para os cenários 1, 2, 3, 7, 9, 11 e 13, rodados no Expo Go (simulador
iOS e emulador Android) contra o backend local com o seed aplicado. Os mesmos arquivos servem às
duas plataformas; o que muda de uma para outra fica em passos com `when: platform`.

## Pré-requisitos

1. **Backend** do Pitaco na porta 8080 (`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`).
2. **Seed** aplicado para o alvo em que os fluxos vão rodar, dentro de `sdk/example/`:
   - iOS: `npm run seed` (o `.env` aponta para `http://localhost:8080/api`);
   - Android: `npm run seed -- --target android-emu` (aponta para `http://10.0.2.2:8080/api`).

   Rodar de novo não duplica nada. Depois de trocar o alvo, reinicie o Metro com `-c`.
3. **Metro** na porta 8081: `npx expo start` dentro de `sdk/example/`. Não use `CI=1`: nesse modo o
   Metro não observa os arquivos e o app pode rodar código velho.
4. **Simulador iOS ou emulador Android** ligado, com o Expo Go instalado (o `npx expo start --ios`
   ou `--android` instala na primeira vez).
5. **Maestro** instalado (`maestro --version`).

## Rodar

```bash
npm run e2e:ios                                    # todos os fluxos no simulador iOS
npm run e2e:android                                # todos os fluxos no emulador Android
maestro --platform android test .maestro/01-sheet.yaml   # um só
```

Com o simulador e o emulador ligados ao mesmo tempo, informe a plataforma (os scripts já
informam) ou o aparelho (`maestro --device emulator-5554 test .maestro/`).

Cada fluxo começa pelo subfluxo `_comum/abrir-cenario.yaml`, que:

- fecha o Expo Go (`_comum/fechar-app.yaml`) e abre o app direto na tela do cenário pelo deep link
  (`_comum/abrir-link.yaml`);
- fecha o menu de desenvolvedor do Expo Go se ele aparecer;
- toca em "Limpar storage e identidade" no painel de depuração.

O servidor não mostra de novo uma pesquisa já respondida ou dispensada ao mesmo respondente, então
cada volta usa um `deviceId` novo.

Os fluxos conferem o resultado na tela, em geral na aba Depuração. Por exemplo, a linha
`survey_dismissed · via swipe` ou o `survey_completed`.

## O que muda entre iOS e Android

| Ponto | iOS | Android |
| --- | --- | --- |
| Endereço do Metro no deep link | `exp://127.0.0.1:8081` | `exp://10.0.2.2:8081` |
| Fechar o Expo Go | `stopApp` (pacote `host.exp.Exponent`) | `stopApp: host.exp.exponent` |
| Fechar o menu de desenvolvedor | X do cartão, por posição | voltar do sistema (`back`) |
| Sair da pesquisa como tela (fluxo 3) | seta do cabeçalho | voltar do sistema |
| Voltar do sistema no sheet do SDK (fluxo 1) | não existe | `back`, com `via hardware_back` |
| Teclado no texto livre (fluxo 9) | `hideKeyboard` | rolagem até o botão, teclado aberto |
| Tutorial de caneta do Gboard (fluxos 2 e 9) | não existe | "Cancel", se aparecer |
| Expo Go frio engole o link | não aconteceu | abre antes a tela do Expo Go, depois o link (`retry` como rede) |

Por quê:

- **Deep link.** No emulador, `127.0.0.1` é o próprio emulador e depende do `adb reverse tcp:8081
  tcp:8081` que o `expo start --android` cria. Neste ambiente o túnel aparecia na lista mas não
  respondia (conexão sem nenhum byte), e o Expo Go ficava no carregamento. `10.0.2.2` é o computador
  visto pelo emulador e funciona sem túnel.
- **Nome do pacote.** O `appId` dos fluxos é o do iOS. No Android o pacote do Expo Go é todo em
  minúsculas, e o nome diferencia maiúsculas: sem o argumento, o `stopApp` não fechava nada, e o
  estado de um fluxo (pilha de navegação, pesquisa em andamento) vazava para o seguinte.
- **`hideKeyboard` no Android.** Quando não acha outro jeito, o Maestro fecha o teclado com o voltar
  do sistema. No cenário 9 isso sai da tela e dispensa a pesquisa (`via navigation`), então o fluxo
  rola até "Concluir" com o teclado aberto.
- **Tutorial de caneta do Gboard.** No emulador, ao abrir o teclado, o Gboard às vezes mostra "Try
  out your stylus" por cima da tela inteira. Não é do app: é o tutorial do teclado. Desligar a
  escrita à mão do sistema (`adb shell settings put secure stylus_handwriting_enabled 0`) não
  bastou, então os fluxos que digitam tocam em "Cancel" quando ele aparece
  (`_comum/fechar-tutorial-teclado.yaml`).
- **Expo Go frio engole o link.** Logo depois de o Expo Go ser fechado, às vezes ele recebe o link e
  não abre o app: fica a tela inicial do Android (no `adb logcat`, o START do `LauncherActivity` com
  o link e uma transição CLOSE, sem o START do `ExperienceActivity`), e o link atrasado ainda pode
  mandar o app para o fundo depois. `_comum/abrir-link.yaml` abre antes a tela inicial do próprio
  Expo Go (`launchApp: host.exp.exponent`), espera por "Expo Go" e só então manda o link; se a tela
  do cenário não vier em 30 s, tenta de novo. O `retry` cobre só a abertura.

## Limitações

- O gesto de voltar da borda no iOS não foi validado em simulador. Nem o swipe sintético do
  Maestro nem um arrasto com o mouse no Simulator acionaram o reconhecedor de borda: a tela não se
  moveu, embora os toques cheguem ao app. O cenário 3 confere a saída pela seta do cabeçalho (iOS)
  e pelo voltar do sistema (Android), que passam pelo mesmo caminho (a rota sai e o
  `<PitacoSurveyContent />` desmonta com `via: "navigation"`). Confira o gesto num aparelho físico.
- O cenário 13 fecha e reabre o Expo Go (`fechar-app.yaml` + deep link). O interruptor "Simular sem
  rede" é só de memória, então o app sempre volta com rede.
- A engrenagem flutuante do Expo Go (só existe no Expo Go, numa janela à parte) fica no canto
  superior direito e cobre o X do modal em tela cheia do cenário 10. Um toque ali abre o menu de
  desenvolvedor. No Android, desligue-a em Expo Go → Settings → "Tools button"; no iOS não há essa
  opção, então arraste a engrenagem para outro canto antes de tocar no X (o Expo Go guarda a
  posição).
- Alguns toques são por posição, porque o elemento não aparece para o Maestro:
  - o fundo escurecido da folha do SDK, que a folha esconde da acessibilidade com
    `accessibilityViewIsModal`;
  - o campo de texto livre no gorhom.

  O rótulo "Fechar pesquisa" também está na alça da folha do SDK, por isso os fluxos fecham pelo
  fundo, e não pelo rótulo.
