# Pitaco React Native SDK

O SDK oficial do Pitaco para React Native e Expo. Sem módulos nativos, com suporte a qualquer dependência existente no seu app.

## Instalação

```bash
npm install @pitaco/react-native
# ou yarn add, expo install, etc.
```

O SDK exige `react >= 18.2` e `react-native >= 0.74` como peer dependencies. Ele **não** exige nenhum módulo nativo extra (como reanimated ou gesture-handler).

## Do zero à primeira pesquisa

Este guia reproduz o **Cenário 1** do app de exemplo: a integração mínima usando a apresentação padrão em Bottom Sheet.

**1. Adicione o Provider na raiz do seu app:**

```tsx
import { PitacoProvider } from '@pitaco/react-native';

export default function App() {
  return (
    <PitacoProvider
      baseUrl="https://pitaco.sua-empresa.com/api"
      apiKey="pk_SUA_CHAVE_AQUI"
    >
      <SuaArvoreDeNavegacao />
    </PitacoProvider>
  );
}
```

**2. Dispare um evento em uma tela:**

```tsx
import { Button } from 'react-native';
import { usePitaco } from '@pitaco/react-native';

export function TelaDeSucesso() {
  const pitaco = usePitaco();

  return (
    <Button
      title="Concluir Pedido"
      onPress={() => {
        pitaco.track('pedido_concluido');
      }}
    />
  );
}
```

E é isso. Se houver uma pesquisa elegível para este usuário e para este evento, o Pitaco abrirá um Bottom Sheet nativo, sem bloquear a interface.

---

## Apresentação

O SDK traz três formas de apresentar a pesquisa: Bottom Sheet (padrão), Modal e Inline.

<p align="center">
  <img src="../example/docs/capturas/ios-sheet-claro.png" width="30%" alt="iOS Sheet Claro" />
  <img src="../example/docs/capturas/android-sheet-escuro.png" width="30%" alt="Android Sheet Escuro" />
  <img src="../example/docs/capturas/ios-tela-claro.png" width="30%" alt="iOS Tela Claro" />
</p>

*As capturas acima mostram, lado a lado, o Bottom Sheet nativo do SDK no iOS (claro), no Android (escuro) e a pesquisa renderizada em tela cheia.*

---

## Referência da API

### `PitacoProvider`

Envolve a árvore do seu aplicativo.

| Propriedade | Tipo | Padrão | Descrição |
| --- | --- | --- | --- |
| `baseUrl` | `string` | **obrigatório** | A URL base do seu servidor Pitaco. |
| `apiKey` | `string` | **obrigatório** | A chave pública do SDK (`pk_...`). |
| `respondent` | `{ reference?: string }` | `{}` | Identificação do usuário. Se `reference` for omitido, o SDK usará um `deviceId` anônimo gerado localmente. |
| `attributes` | `Record<string, string>`| `{}` | Atributos para segmentação (ex: `{ plano: "pro" }`). |
| `storage` | `SafeStorage` | Memória | Adaptador de persistência (AsyncStorage ou MMKV). |
| `presentation` | `'bottom-sheet' \| 'modal' \| 'inline'` | `'bottom-sheet'` | Qual contêiner padrão usar. |
| `theme` | `PitacoThemeConfig` | Padrão | Customização de cores, espaçamentos, raio e fontes. |
| `strings` | `PartialPitacoStrings` | pt-BR | Substituição de textos da interface padrão. |
| `renderers` | `Partial<PitacoRendererMap>` | Padrão | Substituição do visual de cada tipo de pergunta. |
| `slots` | `Partial<PitacoSlotMap>` | Padrão | Substituição de partes da moldura (header, footer, etc). |
| `onEvent` | `(event: PitacoEvent) => void` | `undefined` | Escuta eventos do SDK para repassar ao seu Analytics. |
| `errorReporting` | `boolean` | `true` | Envia relatórios anônimos de erro ao backend do Pitaco. |
| `debug` | `boolean` | `__DEV__` | Mostra avisos de integração no console. |
| `eligibilityTimeoutMs`| `number` | `3000` | Tempo máximo (em ms) para a API responder à elegibilidade. |

### `usePitaco()`

Retorna os métodos de controle do SDK:

- `track(eventName: string, attributes?: Record<string, string>)`: Dispara um evento para possivelmente exibir uma pesquisa.
- `setRespondent(respondent: { reference?: string })`: Atualiza o usuário atual.
- `setAttributes(attributes: Record<string, string>)`: Atualiza os atributos de segmentação.
- `reset()`: Limpa a sessão atual (ideal para logout). Dispensa pesquisa em andamento.
- `block(reason: string)` e `unblock(reason: string)`: Impede/libera a exibição de pesquisas.
- `defer()` e `release()`: Adia a exibição de uma pesquisa pronta para um momento mais oportuno.

---

## Privacidade e Segurança

O SDK do Pitaco foi desenhado para ser seguro por padrão:

- **O que enviamos:** Respostas estruturadas (valores numéricos e escolhas predefinidas), tamanho de texto livre digitado (nunca o conteúdo), tempos de resposta e identificadores opacos.
- **O que NUNCA enviamos:** Textos livres digitados pelos usuários. Nenhuma ferramenta de terceiros ou Analytics próprio do seu app recebe dados de respostas do SDK automaticamente.
- **⚠️ Aviso importante:** **Nunca** envie dados pessoais identificáveis (PII, como nome, email, CPF ou telefone) em `attributes` ou na propriedade `reference`. Use hashes (como SHA-256) do ID do seu banco de dados se precisar conciliar dados.

---

## Por que a pesquisa não apareceu?

Em produção, o Pitaco degrada silenciosamente e nunca quebra o seu app. Se uma pesquisa não aparecer quando você espera, verifique o **Painel de Depuração** no app de exemplo (`sdk/example`). Motivos comuns de silêncio:

1. **Limite por sessão:** Por padrão, apenas uma pesquisa é exibida por sessão do aplicativo.
2. **Tempo de espera estourado:** Se a rede estiver lenta e a API demorar mais que o `eligibilityTimeoutMs` (3s).
3. **Bloqueio ativo:** Você chamou `block()` (ou está renderizando `<PitacoBlock />`) e não liberou.
4. **Falta de rede / Backend inacessível:** A requisição falhou, e o SDK desistiu para não impactar a performance.
5. **Critérios do servidor:** O servidor negou (ex: já respondeu antes, cota atingida, não está na amostragem).
6. **Versão incompatível:** A pesquisa requer perguntas que a versão do SDK instalada não conhece.

---

## Guias de Integração Customizada

### Com `@gorhom/bottom-sheet` (Cenário 2)

O Pitaco não exige o Gorhom, mas convive perfeitamente com ele. Para usar, ajuste `presentation="inline"` e desenhe o sheet no seu app:

```tsx
import { BottomSheetModal } from '@gorhom/bottom-sheet';
import { PitacoSurveyContent, usePitacoSurvey } from '@pitaco/react-native';

export function MeuSheetGorhom() {
  const { available, present, dismiss } = usePitacoSurvey();

  // Quando `available` for true, você abre o seu BottomSheetModal.
  // Dentro dele, renderize:
  return (
    <BottomSheetModal onDismiss={() => dismiss('swipe')}>
      <PitacoSurveyContent presentDeferred={true} />
    </BottomSheetModal>
  );
}
```

### Como tela no React Navigation (Cenário 3)

Se preferir que a pesquisa seja uma tela em vez de um modal:

```tsx
import { PitacoSurveyContent } from '@pitaco/react-native';

// Na sua configuração de rota, direcione para este componente:
export function TelaDePesquisa({ navigation }) {
  return (
    <PitacoSurveyContent
      onFinish={() => navigation.goBack()}
    />
  );
}
```

### UI 100% Headless (Cenário 9)

Você pode descartar a UI padrão inteiramente e construir a sua usando o estado do core:

```tsx
import { usePitacoSurvey } from '@pitaco/react-native';

export function MinhaPesquisaCustomizada() {
  const { status, question, select, next } = usePitacoSurvey();

  if (status !== 'presented' || !question) return null;

  return (
    <View>
      <Text>{question.statement}</Text>
      <Button title="Próxima" onPress={() => next()} />
    </View>
  );
}
```

---

## Catálogo de Eventos

O SDK emite eventos estruturados para que você envie ao seu Analytics via a prop `onEvent` do `PitacoProvider`.

Envelope comum a todos os eventos:
```json
{
  "catalogVersion": 1,
  "displayId": "uuid-v4",
  "seq": 1,
  "occurredAt": "2026-09-14T10:00:00.000Z",
  "elapsedMs": 1500,
  "type": "...",
  "data": { ... }
}
```
*(Eventos referentes a uma pergunta específica incluem a propriedade `"questionKey"` ao lado do `type`)*.

### Exemplos de Payload (`data`) por Tipo:

1. **`survey_presented`**: O contêiner ficou visível.
   `{ "presentation": "bottom-sheet", "questionCount": 3, "renderableCount": 3, "triggerEvent": "pedido_concluido" }`
2. **`question_viewed`**: Uma pergunta passou a ser a atual.
   `{ "position": 1, "visit": 1, "from": "start" }`
3. **`answer_selected`**: Primeira escolha ou opção adicionada.
   `{ "value": "opcao_1" }`
4. **`answer_changed`**: Escolha trocada.
   `{ "from": 5, "to": 8 }`
5. **`answer_deselected`**: Opção removida ou limpa.
   `{ "value": "opcao_1" }`
6. **`text_focused`**: Campo de texto recebeu foco.
   `{}`
7. **`text_edited`**: Texto alterado (debounce 1s).
   `{ "length": 42 }`
8. **`text_blurred`**: Campo perdeu o foco.
   `{ "length": 42 }`
9. **`validation_blocked`**: Tentou avançar sem responder obrigatória.
   `{ "reason": "required_missing" }`
10. **`question_skipped`**: Avançou pergunta opcional em branco.
    `{}`
11. **`question_not_applicable`**: Condição pulou a pergunta.
    `{ "sourceKey": "pergunta_1" }`
12. **`navigated_next`**: Avançou (botão Próxima).
    `{ "toKey": "pergunta_3" }`
13. **`navigated_back`**: Voltou (botão Voltar).
    `{ "toKey": "pergunta_1" }`
14. **`question_left`**: A pergunta deixou de ser a atual.
    `{ "visit": 1, "to": "next", "durationMs": 3500, "activeMs": 3500, "answered": true }`
15. **`survey_backgrounded`**: O app foi para segundo plano.
    `{}`
16. **`survey_foregrounded`**: O app voltou com a pesquisa aberta.
    `{ "backgroundMs": 15000 }`
17. **`survey_dismissed`**: O usuário fechou a pesquisa antes do fim.
    `{ "via": "swipe", "position": 2, "answeredCount": 1 }`
18. **`survey_completed`**: Enviou a última pergunta.
    `{ "answeredCount": 3, "skippedCount": 0, "notApplicableCount": 0, "activeMs": 12500 }`
