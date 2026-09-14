// Teste de contrato (definição de pronto do projeto): para o mesmo roteiro de interação, a UI
// padrão (`<PitacoSurveyContent />` com os renderizadores de fábrica), a mesma UI com um
// renderizador substituído (NPS e escolha única próprios) e uma UI headless mínima sobre
// `usePitacoSurvey()` produzem exatamente a mesma sequência de eventos do catálogo (mesmo tipo,
// ordem, `questionKey` e `data`, tempos à parte) que a máquina pura sobre o mesmo roteiro
// (`src/__tests__/support/script.ts`).
//
// As três UIs rodam sobre `PitacoPreviewProvider` (schema em memória, sem transporte): mais
// simples que montar `PitacoProvider` de verdade, e suficiente aqui porque `background`/
// `foreground` (que dependem do `AppState` do app, não do preview) já têm a própria prova de
// paridade em `src/react/__tests__/hooks.test.tsx` (headless sobre o Provider de verdade) —
// filtrados aqui como o resto da suíte de preview já faz.

import { act, fireEvent, render, screen } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';
import type { InteractionEvent } from '../../../catalog/events';
import { deliverableSurvey, referenceSurvey } from '../../../__tests__/support/fixtures';
import { createMachineDriver, runScript, type ScriptDriver, type ScriptStep, signature, STANDARD_SCRIPT, DISMISS_SCRIPT } from '../../../__tests__/support/script';
import { PitacoPreviewProvider } from '../../../preview';
import { usePitacoSurvey } from '../../../react/usePitacoSurvey';
import type { UsePitacoSurveyResult } from '../../../react/usePitacoSurvey';
import type { PitacoRendererMap, QuestionRendererProps } from '../../types';
import { PitacoSurveyContent } from '../PitacoSurveyContent';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

// Passos que só um contêiner de verdade (`AppState`, fase 3c) sabe produzir — sem paridade nesta
// suíte, que roda sobre o preview (ver o comentário do arquivo).
function withoutAppState(script: readonly ScriptStep[]): readonly ScriptStep[] {
  return script.filter((step) => step.do !== 'background' && step.do !== 'foreground');
}

// --- Dois renderizadores próprios: mesmo contrato de acessibilidade dos padrões (papel `radio`,
// rótulo igual), visual completamente diferente. A escolha única própria, ao contrário da
// padrão, alterna select/deselect ao tocar de novo — prova que o contrato de eventos não depende
// de como (nem se) cada UI expõe o gesto de desmarcar.

function CustomNps(props: QuestionRendererProps): ReactNode {
  const { question, value, actions } = props;
  const range = question.range ?? { min: 0, max: 10, minLabel: null, maxLabel: null };
  const options: number[] = [];
  for (let option = range.min; option <= range.max; option += 1) options.push(option);
  return (
    <View>
      {options.map((option) => (
        <Pressable
          key={option}
          accessibilityRole="radio"
          accessibilityState={{ checked: value === option }}
          accessibilityLabel={String(option)}
          onPress={() => actions.select(option)}
        >
          <Text>[{option}]</Text>
        </Pressable>
      ))}
    </View>
  );
}

function CustomSingleChoice(props: QuestionRendererProps): ReactNode {
  const { question, value, actions } = props;
  return (
    <View>
      {question.options.map((option) => {
        const selected = value === option.value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityState={{ checked: selected }}
            accessibilityLabel={option.label}
            onPress={() => (selected ? actions.deselect(option.value) : actions.select(option.value))}
          >
            <Text>» {option.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const CUSTOM_RENDERERS: Partial<PitacoRendererMap> = { nps: CustomNps, singleChoice: CustomSingleChoice };

// Espião headless: entrega o `usePitacoSurvey()` mais recente a quem monta a árvore, sem
// desenhar nada — usado pelos três drivers para ler o estado atual (pergunta, tipo) e, quando a
// UI não tem gesto para uma ação (dispensa que não seja pelo botão de fechar; desmarcar escolha
// única na UI padrão), chamar a ação pública direto — exatamente o que o próprio contêiner do
// app faria (contrato documentado na fase 3c: `close_button` nasce do conteúdo, as outras vias
// vêm de fora, direto em `dismiss(via)`).
function SurveyBridge(props: { readonly onSurvey: (survey: UsePitacoSurveyResult) => void }) {
  const survey = usePitacoSurvey();
  props.onSurvey(survey);
  return null;
}

function locatorFor(
  question: NonNullable<UsePitacoSurveyResult['question']>,
  value: string | number,
): { role: 'radio' | 'checkbox'; name: string } {
  switch (question.type) {
    case 'NPS':
    case 'SCALE':
      return { role: 'radio', name: String(value) };
    case 'SINGLE_CHOICE': {
      const option = question.options.find((candidate) => candidate.value === value);
      return { role: 'radio', name: option?.label ?? String(value) };
    }
    case 'MULTIPLE_CHOICE': {
      const option = question.options.find((candidate) => candidate.value === value);
      return { role: 'checkbox', name: option?.label ?? String(value) };
    }
    case 'RATING': {
      const range = question.range!;
      const total = range.max - range.min + 1;
      const position = Number(value) - range.min + 1;
      return { role: 'radio', name: `${position} de ${total}` };
    }
    default:
      throw new Error(`sem localizador para o tipo ${question.type}`);
  }
}

const FREE_TEXT_PLACEHOLDER = 'Digite sua resposta';

// Driver comum às duas UIs renderizadas (padrão e substituída): rating, múltipla escolha e texto
// livre usam sempre os renderizadores de fábrica nas duas (só NPS e escolha única mudam), então a
// mesma lógica de toque vale para ambas — `singleChoiceDeselectViaUi` é o único ponto que muda.
function renderedUiDriver(getSurvey: () => UsePitacoSurveyResult, singleChoiceDeselectViaUi: boolean): ScriptDriver {
  return {
    perform: async (step) => {
      const survey = getSurvey();
      switch (step.do) {
        case 'present':
          // `<PitacoSurveyContent />` já chamou `present()` sozinha, num efeito de montagem,
          // antes do primeiro passo do roteiro rodar — nada a fazer aqui.
          return;
        case 'select': {
          const { role, name } = locatorFor(survey.question!, step.value);
          await act(async () => fireEvent.press(screen.getByRole(role, { name })));
          return;
        }
        case 'deselect': {
          if (step.value === undefined) {
            await act(() => survey.deselect());
            return;
          }
          if (survey.question!.type === 'SINGLE_CHOICE' && !singleChoiceDeselectViaUi) {
            // A escolha única padrão nunca desmarca ao tocar (decisão da fase 3b): sem gesto de
            // UI para isto, é o app quem chamaria a ação pública direto.
            await act(() => survey.deselect(step.value));
            return;
          }
          const { role, name } = locatorFor(survey.question!, step.value);
          await act(async () => fireEvent.press(screen.getByRole(role, { name })));
          return;
        }
        case 'focus':
          await act(async () => fireEvent(screen.getByPlaceholderText(FREE_TEXT_PLACEHOLDER), 'focus'));
          return;
        case 'type':
          await act(async () => fireEvent.changeText(screen.getByPlaceholderText(FREE_TEXT_PLACEHOLDER), step.text));
          return;
        case 'blur':
          await act(async () => fireEvent(screen.getByPlaceholderText(FREE_TEXT_PLACEHOLDER), 'blur'));
          return;
        case 'next':
        case 'complete': {
          const button = screen.queryByRole('button', { name: 'Próxima' }) ?? screen.getByRole('button', { name: 'Enviar' });
          await act(async () => fireEvent.press(button));
          return;
        }
        case 'back':
          await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Voltar' })));
          return;
        case 'dismiss':
          if (step.via === 'close_button') {
            await act(async () => fireEvent.press(screen.getByRole('button', { name: 'Fechar pesquisa' })));
          } else {
            // Só o botão de fechar nasce de dentro do conteúdo; as outras vias vêm do contêiner
            // (fase 3c) — não desenhado aqui, então a UI headless do próprio app chama direto.
            await act(() => survey.dismiss(step.via));
          }
          return;
        case 'background':
        case 'foreground':
          throw new Error('filtrado por withoutAppState — não deveria chegar aqui');
      }
    },
    wait: async (ms) => {
      await act(async () => {
        await jest.advanceTimersByTimeAsync(ms);
      });
    },
  };
}

// Driver headless mínimo: exatamente o que uma UI própria, sem nenhum componente do Pitaco,
// faria só com `usePitacoSurvey()`.
function headlessDriver(getSurvey: () => UsePitacoSurveyResult): ScriptDriver {
  return {
    perform: async (step) => {
      const survey = getSurvey();
      await act(() => {
        switch (step.do) {
          case 'present':
            return survey.present('bottom-sheet');
          case 'select':
            return survey.select(step.value);
          case 'deselect':
            return survey.deselect(step.value);
          case 'focus':
            return survey.focusText();
          case 'type':
            return survey.setText(step.text);
          case 'blur':
            return survey.blurText();
          case 'next':
            return survey.next();
          case 'back':
            return survey.back();
          case 'complete':
            return survey.complete();
          case 'dismiss':
            return survey.dismiss(step.via);
          case 'background':
          case 'foreground':
            throw new Error('filtrado por withoutAppState — não deveria chegar aqui');
        }
      });
    },
    wait: async (ms) => {
      await act(async () => {
        await jest.advanceTimersByTimeAsync(ms);
      });
    },
  };
}

let fetchSpy: jest.Mock;

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
  fetchSpy = jest.fn(() => Promise.reject(new Error('o preview não fala com a rede')));
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchSpy });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('mesma sequência de eventos: UI padrão, UI com renderizador substituído e UI headless', () => {
  it.each([
    ['roteiro completo', STANDARD_SCRIPT],
    ['roteiro de dispensa', DISMISS_SCRIPT],
  ])('%s: as três produzem a mesma assinatura da máquina de referência', async (_name, script) => {
    const filtered = withoutAppState(script);

    const reference = createMachineDriver(referenceSurvey());
    await runScript(reference.driver, filtered);
    const expectedSignature = signature(reference.events);

    // (a) UI padrão.
    {
      const events: InteractionEvent[] = [];
      let survey: UsePitacoSurveyResult;
      await render(
        <PitacoPreviewProvider schema={deliverableSurvey()} triggerEvent="checkout_completed" onEvent={(event) => events.push(event)}>
          <SurveyBridge onSurvey={(value) => (survey = value)} />
          <PitacoSurveyContent />
        </PitacoPreviewProvider>,
      );
      await runScript(renderedUiDriver(() => survey, false), filtered);
      expect(signature(events)).toEqual(expectedSignature);
    }

    // (b) UI com NPS e escolha única substituídos.
    {
      const events: InteractionEvent[] = [];
      let survey: UsePitacoSurveyResult;
      await render(
        <PitacoPreviewProvider
          schema={deliverableSurvey()}
          triggerEvent="checkout_completed"
          onEvent={(event) => events.push(event)}
          renderers={CUSTOM_RENDERERS}
        >
          <SurveyBridge onSurvey={(value) => (survey = value)} />
          <PitacoSurveyContent />
        </PitacoPreviewProvider>,
      );
      await runScript(renderedUiDriver(() => survey, true), filtered);
      expect(signature(events)).toEqual(expectedSignature);
    }

    // (c) UI headless mínima.
    {
      const events: InteractionEvent[] = [];
      let survey: UsePitacoSurveyResult;
      await render(
        <PitacoPreviewProvider schema={deliverableSurvey()} triggerEvent="checkout_completed" onEvent={(event) => events.push(event)}>
          <SurveyBridge onSurvey={(value) => (survey = value)} />
        </PitacoPreviewProvider>,
      );
      await runScript(headlessDriver(() => survey), filtered);
      expect(signature(events)).toEqual(expectedSignature);
    }

    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
