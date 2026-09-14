import { createContext } from 'react';
import type { PitacoRuntime } from '../core/runtime/runtime';
import type { SurveyController } from '../core/session/controller';
import type { PitacoUiConfig } from '../ui/types';

export interface PitacoContextValue {
  // Nulo quando a configuração é inválida (SDK desligado) ou no preview, que não tem transporte.
  readonly runtime: PitacoRuntime | null;
  readonly controller: SurveyController | null;
  // Segundo parâmetro opcional: contexto técnico extra do relatório de erro (fase 3), como o
  // slot ou o tipo de pergunta cujo renderizador substituído falhou.
  readonly reportRenderError: (error: unknown, context?: Readonly<Record<string, string>>) => void;
  // Tema, textos, renderizadores, slots e apresentação configurados no Provider — a fase 3 lê
  // daqui. Sempre presente (com o padrão de `presentation`), mesmo fora do Provider real (o
  // preview entrega o seu próprio, com `bottom-sheet` como padrão).
  readonly ui: PitacoUiConfig;
}

export const PitacoContext = createContext<PitacoContextValue | null>(null);
