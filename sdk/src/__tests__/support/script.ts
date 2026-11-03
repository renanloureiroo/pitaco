// Roteiro de interação reutilizável. O mesmo roteiro roda sobre a máquina pura, sobre o hook
// headless e, com a UI padrão, sobre os componentes (apertando botões); a assinatura dos eventos
// produzidos precisa ser a mesma nos três. Cada driver sabe traduzir um passo para a sua camada.

import type { DismissVia, InteractionEvent } from '../../catalog/events';
import { type Instant } from '../../core/clock';
import { createInitialState, type MachineState, type SurveyAction, transition } from '../../core/machine/machine';
import type { Survey } from '../../core/survey/schema';
import { createFakeClock } from './fakeClock';
import { KEYS } from './fixtures';

export type ScriptStep =
  | { readonly do: 'present' }
  | { readonly do: 'select'; readonly value: string | number }
  | { readonly do: 'deselect'; readonly value?: string | number }
  | { readonly do: 'focus' }
  | { readonly do: 'type'; readonly text: string }
  | { readonly do: 'blur' }
  | { readonly do: 'next' }
  | { readonly do: 'back' }
  | { readonly do: 'complete' }
  | { readonly do: 'dismiss'; readonly via: DismissVia }
  | { readonly do: 'background' }
  | { readonly do: 'foreground' }
  | { readonly do: 'wait'; readonly ms: number };

export interface ScriptDriver {
  perform(step: Exclude<ScriptStep, { do: 'wait' }>): void | Promise<void>;
  wait(ms: number): void | Promise<void>;
}

export async function runScript(driver: ScriptDriver, steps: readonly ScriptStep[]): Promise<void> {
  for (const step of steps) {
    if (step.do === 'wait') {
      await driver.wait(step.ms);
    } else {
      await driver.perform(step);
    }
  }
}

export function toAction(step: Exclude<ScriptStep, { do: 'wait' }>): SurveyAction {
  switch (step.do) {
    case 'present':
      return { type: 'present', presentation: 'bottom-sheet' };
    case 'select':
      return { type: 'select', value: step.value };
    case 'deselect':
      return step.value === undefined ? { type: 'deselect' } : { type: 'deselect', value: step.value };
    case 'focus':
      return { type: 'focusText' };
    case 'type':
      return { type: 'setText', text: step.text };
    case 'blur':
      return { type: 'blurText' };
    case 'next':
      return { type: 'next' };
    case 'back':
      return { type: 'back' };
    case 'complete':
      return { type: 'complete' };
    case 'dismiss':
      return { type: 'dismiss', via: step.via };
    case 'background':
      return { type: 'background' };
    case 'foreground':
      return { type: 'foreground' };
  }
}

// O que precisa ser igual entre UIs: tipo, pergunta e payload. Tempos dependem do relógio de
// cada ambiente e ficam de fora.
const TIMING_FIELDS = new Set(['durationMs', 'activeMs', 'backgroundMs']);

export function signature(events: readonly InteractionEvent[]): string[] {
  return events.map((event) => {
    const data = Object.fromEntries(
      Object.entries(event.data as Record<string, unknown>).filter(([key]) => !TIMING_FIELDS.has(key)),
    );
    const question = 'questionKey' in event ? `@${event.questionKey}` : '';
    return `${event.seq} ${event.type}${question} ${JSON.stringify(data)}`;
  });
}

// Driver da máquina pura, com relógio falso: a referência contra a qual as UIs são comparadas.
export function createMachineDriver(survey: Survey, displayId = 'd0000000-0000-4000-8000-000000000000') {
  const clock = createFakeClock();
  let state: MachineState = createInitialState({ survey, displayId, triggerEvent: 'checkout_completed' });
  const events: InteractionEvent[] = [];
  const apply = (action: SurveyAction, now: Instant = clock.now()) => {
    const result = transition(state, action, now);
    state = result.state;
    events.push(...result.events);
  };
  const driver: ScriptDriver = {
    perform: (step) => apply(toAction(step)),
    wait: (ms) => {
      // O debounce de texto é resolvido pelo `tick` que a sessão agenda; aqui, a cada segundo.
      let remaining = ms;
      while (remaining > 0) {
        const slice = Math.min(remaining, 250);
        clock.advance(slice);
        remaining -= slice;
        apply({ type: 'tick' });
      }
    },
  };
  return { driver, events, clock, get state() { return state; } };
}

// Roteiro de referência sobre a pesquisa de `fixtures.ts`. Passa por todos os 18 tipos do
// catálogo, menos `survey_dismissed` (que tem roteiro próprio, `DISMISS_SCRIPT`).
export const STANDARD_SCRIPT: readonly ScriptStep[] = [
  { do: 'present' },
  { do: 'wait', ms: 400 },
  { do: 'next' }, // validation_blocked: NPS é obrigatória
  { do: 'select', value: 9 },
  { do: 'select', value: 3 }, // answer_changed
  { do: 'wait', ms: 600 },
  { do: 'next' }, // nota 3: a de motivo se aplica
  { do: 'select', value: 'preco' },
  { do: 'deselect', value: 'preco' }, // answer_deselected
  { do: 'next' }, // question_skipped (opcional em branco)
  { do: 'select', value: 'pix' },
  { do: 'select', value: 'boleto' },
  { do: 'back' }, // navigated_back
  { do: 'wait', ms: 200 },
  { do: 'next' },
  { do: 'background' },
  { do: 'wait', ms: 3000 },
  { do: 'foreground' },
  { do: 'deselect', value: 'pix' },
  { do: 'next' },
  { do: 'select', value: 4 },
  { do: 'next' },
  { do: 'focus' },
  { do: 'type', text: 'o' },
  { do: 'type', text: 'ok' },
  { do: 'wait', ms: 1200 }, // text_edited com debounce
  { do: 'type', text: 'ok!' },
  { do: 'blur' }, // text_edited pendente sai antes do text_blurred
  { do: 'complete' },
];

// Dispensa na segunda pergunta, preservando a primeira; nota alta pula a de motivo.
export const DISMISS_SCRIPT: readonly ScriptStep[] = [
  { do: 'present' },
  { do: 'select', value: 10 },
  { do: 'next' }, // question_not_applicable para a de motivo
  { do: 'select', value: 'cartao' },
  { do: 'dismiss', via: 'swipe' },
];

export { KEYS };
