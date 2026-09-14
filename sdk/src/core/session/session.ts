// Uma exibição em andamento: segura o estado da máquina, aplica as ações com o relógio, agenda o
// debounce do texto e entrega os eventos a quem criou (o runtime, que grava na fila, ou o preview,
// que só repassa ao `onEvent`). Não sabe nada de rede.

import type { InteractionEvent } from '../../catalog/events';
import type { Clock } from '../clock';
import type { Timers, TimerHandle } from '../timers';
import {
  createInitialState,
  type MachineState,
  type SurveyAction,
  transition,
} from '../machine/machine';
import type { Survey } from '../survey/schema';
import { deriveSnapshot, type SurveySnapshot } from './snapshot';

export interface SurveySessionOptions {
  readonly survey: Survey;
  readonly displayId: string;
  readonly triggerEvent: string;
  readonly clock: Clock;
  readonly timers: Timers;
  readonly onTransition: (events: readonly InteractionEvent[], state: MachineState) => void;
  readonly onError?: (error: unknown, stage: string) => void;
}

export class SurveySession {
  private state: MachineState;
  private snapshot: SurveySnapshot;
  private readonly listeners = new Set<() => void>();
  private editTimer: TimerHandle | null = null;
  private disposed = false;

  constructor(private readonly options: SurveySessionOptions) {
    this.state = createInitialState(options);
    this.snapshot = deriveSnapshot(this.state);
  }

  get machineState(): MachineState {
    return this.state;
  }

  readonly getSnapshot = (): SurveySnapshot => this.snapshot;

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  dispatch(action: SurveyAction): void {
    if (this.disposed) return;
    let result;
    try {
      result = transition(this.state, action, this.options.clock.now());
    } catch (error) {
      this.options.onError?.(error, 'transition');
      return;
    }
    if (result.state === this.state) return;

    this.state = result.state;
    this.snapshot = deriveSnapshot(this.state);
    this.scheduleEditFlush();

    try {
      this.options.onTransition(result.events, this.state);
    } catch (error) {
      this.options.onError?.(error, 'events');
    }
    for (const listener of Array.from(this.listeners)) {
      try {
        listener();
      } catch (error) {
        this.options.onError?.(error, 'listener');
      }
    }
  }

  dispose(): void {
    this.disposed = true;
    this.clearEditTimer();
    this.listeners.clear();
  }

  // `text_edited` sai 1 s depois da última edição. O timer só acorda a máquina com `tick`; quem
  // decide se já venceu é ela, com o relógio.
  private scheduleEditFlush() {
    this.clearEditTimer();
    const pending = this.state.pendingEdit;
    if (pending === null || this.state.status !== 'presented') return;
    const delay = Math.max(0, pending.dueAt - this.options.clock.now().mono);
    this.editTimer = this.options.timers.set(() => {
      this.editTimer = null;
      this.dispatch({ type: 'tick' });
    }, delay);
  }

  private clearEditTimer() {
    if (this.editTimer !== null) {
      this.options.timers.clear(this.editTimer);
      this.editTimer = null;
    }
  }
}
