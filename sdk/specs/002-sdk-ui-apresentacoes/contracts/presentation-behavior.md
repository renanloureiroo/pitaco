# Contrato — comportamento das apresentações

**Feature**: `002-sdk-ui-apresentacoes`

O que cada apresentação faz, quem chama `present()` e `dismiss(via)`, e os identificadores de
teste estáveis.

---

## Quem chama o quê

| Momento | `bottom-sheet` / `modal` (SDK) | `inline` (app) |
| --- | --- | --- |
| pesquisa disponível | host abre o contêiner | app vê `usePitacoSurvey().available` e monta `<PitacoSurveyContent />` onde quiser |
| ficou visível | host chama `present()` no fim da entrada | conteúdo chama `present()` ao montar, ou o contêiner do app com `presentDeferred` |
| botão de fechar | conteúdo → `dismiss('close_button')` | igual |
| toque no fundo | host → `dismiss('backdrop')` (só sheet) | contêiner do app → `dismiss('backdrop')` |
| arrastar | host → `dismiss('swipe')` (só sheet) | contêiner do app → `dismiss('swipe')` |
| voltar do Android | host → `dismiss('hardware_back')` | contêiner do app → `dismiss('hardware_back')` |
| saída pela navegação | — | desmonte do conteúdo → `dismiss(dismissVia ?? 'navigation')` |
| dispensa vinda de código | `dismiss()` → `programmatic`; host fecha | igual |
| concluir | conteúdo mostra o agradecimento; host fecha depois | conteúdo mostra o agradecimento e chama `onFinish('completed')` |

Uma dispensa já registrada (por qualquer via) impede a segunda no desmonte.

---

## Bottom sheet

| Aspecto | Comportamento |
| --- | --- |
| contêiner | `Modal` transparente, sem animação própria, `statusBarTranslucent` no Android |
| entrada | `translateY` da folha + opacidade do fundo, driver nativo, 280 ms |
| saída | 220 ms; `onClosed` só depois |
| altura | conteúdo rolável até 90% da janela, ancorado embaixo |
| alça | área tocável de ao menos 44 pt, papel `adjustable`, rótulo "Fechar pesquisa", ações `escape` e `magicTap` fecham |
| arrastar | fecha acima de 120 pt ou 0,8 de velocidade; volta animada à posição aberta abaixo; resistência 0,25 para cima |
| fundo | botão "Fechar pesquisa"; toque dispensa com `backdrop` |
| voltar do Android | `BackHandler` + `onRequestClose` → `hardware_back` |
| teclado | `KeyboardAvoidingView` com `flex: 1`, `justifyContent: 'flex-end'`, `padding` (iOS) / `height` (Android) |
| safe area | `paddingBottom` com o inset inferior |
| acessibilidade | folha com `accessibilityViewIsModal` |

## Modal em tela cheia

| Aspecto | Comportamento |
| --- | --- |
| contêiner | `Modal` opaco, `presentationStyle="fullScreen"` no iOS |
| animação | opacidade, 220 ms de entrada e 180 ms de saída |
| arrastar | não há |
| safe area | os quatro insets como padding |
| voltar do Android | `hardware_back` |
| teclado | mesma regra do sheet |

## Inline

O SDK não desenha nada; o host não guarda estado de contêiner aberto.

---

## testIDs estáveis

| testID | Elemento |
| --- | --- |
| `pitaco-bottom-sheet` | a folha |
| `pitaco-bottom-sheet-container` | o `KeyboardAvoidingView` que ancora a folha |
| `pitaco-bottom-sheet-backdrop` | o fundo tocável |
| `pitaco-bottom-sheet-handle` | a alça |
| `pitaco-fullscreen-keyboard-avoider` | o `KeyboardAvoidingView` do modal |
| `pitaco-fullscreen-safe-area` | a área com os insets do modal |

Rótulos visíveis estáveis para automação: "Pergunta X de Y", "Próxima", "Voltar", "Enviar",
"Obrigado!", e o rótulo de acessibilidade "Fechar pesquisa".
