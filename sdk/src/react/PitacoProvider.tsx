import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";
import type { Presentation } from "../catalog/events";
import type { Attributes, Respondent } from "../core/config";
import {
  type PitacoListenerEvent,
  PitacoRuntime,
} from "../core/runtime/runtime";
import type { PitacoStorage } from "../core/storage/types";
import type { PartialPitacoStrings } from "../ui/strings/strings";
import type { PitacoThemeConfig } from "../ui/theme/tokens";
import type {
  EdgeInsets,
  PitacoRendererMap,
  PitacoSlotMap,
  PitacoUiConfig,
} from "../ui/types";
import { PitacoContext, type PitacoContextValue } from "./context";
import { PitacoErrorBoundary } from "./ErrorBoundary";
import { PitacoSurfaceHost } from "./SurfaceHost";

const DEFAULT_PRESENTATION: Presentation = "bottom-sheet";

export interface PitacoProviderProps {
  // Obrigatório: o prefixo que serve `/collect`, do Pitaco ou do gateway do app.
  readonly baseUrl: string;
  // Obrigatório: a chave pública da aplicação.
  readonly apiKey: string;
  // Opcional; sem ele o respondente é o `deviceId`. Referência opaca, nunca dado pessoal.
  readonly respondent?: Respondent | null;
  // Opcional; atributos de segmentação. Só o necessário, nunca dado pessoal.
  readonly attributes?: Attributes;
  // Opcional; sem ele a fila fica em memória e não sobrevive a um reinício do app.
  readonly storage?: PitacoStorage;
  // Padrão `'bottom-sheet'`. `'modal'` é tela cheia; `'inline'` não abre contêiner nenhum (o app
  // posiciona `<PitacoSurveyContent />` onde quiser).
  readonly presentation?: Presentation;
  // Só leitura: o app ouve os eventos, nunca os altera.
  readonly onEvent?: (event: PitacoListenerEvent) => void;
  readonly errorReporting?: boolean;
  readonly debug?: boolean;
  // Tempo máximo da consulta de elegibilidade, em milissegundos. Padrão 3000.
  readonly eligibilityTimeoutMs?: number;
  // Prazo máximo (ms) que uma pesquisa fica retida por `defer()` (ou pela chegada durante um
  // `block()` ativo) antes de ser descartada sem abrir exibição. Padrão 5 minutos.
  readonly deferTimeoutMs?: number;
  // Tema num ponto único (cor, tipografia, raio, espaçamento; claro e escuro). Valor inválido
  // cai no padrão daquele token, com aviso em `__DEV__`. Ver `usePitacoTheme()`.
  readonly theme?: PitacoThemeConfig;
  // Rótulos da interface, em pt-BR por padrão. Ver `usePitacoStrings()`.
  readonly strings?: PartialPitacoStrings;
  // Renderizador por tipo de pergunta (`{ nps: MeuNps }`); cada substituição roda isolada num
  // error boundary e cai no padrão daquele tipo se lançar.
  readonly renderers?: Partial<PitacoRendererMap>;
  // Slots de moldura (`Header`, `Progress`, `Footer`, `CloseButton`, `ThankYou`); mesmo
  // isolamento dos renderizadores.
  readonly slots?: Partial<PitacoSlotMap>;
  // Safe area sem dependência: um valor fixo, ou uma função (por exemplo o próprio
  // `useSafeAreaInsets()` de quem já usa `react-native-safe-area-context`) chamada a cada
  // apresentação. `getInsets` tem prioridade sobre `insets` quando os dois vêm.
  readonly insets?: EdgeInsets;
  readonly getInsets?: () => EdgeInsets;
  readonly children?: ReactNode;
}

function createRuntime(props: PitacoProviderProps) {
  try {
    return PitacoRuntime.create(
      {
        baseUrl: props.baseUrl,
        apiKey: props.apiKey,
        presentation: props.presentation,
        errorReporting: props.errorReporting,
        debug: props.debug,
        eligibilityTimeoutMs: props.eligibilityTimeoutMs,
        deferTimeoutMs: props.deferTimeoutMs,
        respondent: props.respondent ?? null,
        ...(props.attributes === undefined
          ? {}
          : { attributes: props.attributes }),
        ...(props.storage === undefined ? {} : { storage: props.storage }),
        ...(props.onEvent === undefined ? {} : { onEvent: props.onEvent }),
      },
      { appState: AppState },
    );
  } catch {
    return null;
  }
}

function stableKey(value: unknown): string {
  try {
    return JSON.stringify(value ?? null);
  } catch {
    return "null";
  }
}

export function PitacoProvider(props: PitacoProviderProps): ReactNode {
  const {
    children,
    onEvent,
    respondent,
    attributes,
    theme,
    strings,
    renderers,
    slots,
    insets,
    getInsets,
  } = props;

  // O runtime nasce no render (sem efeito colateral nenhum) para um `track` chamado no efeito de
  // montagem de um filho já encontrá-lo; liga ao app no efeito. Muda só quando muda o que define
  // o destino ou o comportamento do transporte.
  const identity = [
    props.baseUrl,
    props.apiKey,
    props.presentation,
    props.errorReporting,
    props.debug,
    props.eligibilityTimeoutMs,
    props.deferTimeoutMs,
  ]
    .map((part) => String(part))
    .join("\u0000");
  const [holder, setHolder] = useState(() => ({
    identity,
    storage: props.storage,
    runtime: createRuntime(props),
  }));
  let current = holder;
  if (holder.identity !== identity || holder.storage !== props.storage) {
    current = {
      identity,
      storage: props.storage,
      runtime: createRuntime(props),
    };
    setHolder(current);
  }
  const runtime = current.runtime;

  useEffect(() => {
    if (runtime === null) return undefined;
    runtime.attach();
    return () => runtime.detach();
  }, [runtime]);

  // O runtime substituído (configuração ou storage mudou) é encerrado de vez: sem isso o
  // temporizador de uma pesquisa retida ainda venceria e emitiria `placement_expired` pelo ouvinte
  // antigo. A desmontagem simples só faz `detach`, porque o StrictMode desmonta e monta de novo o
  // mesmo runtime.
  const previousRuntime = useRef(runtime);
  useEffect(() => {
    const previous = previousRuntime.current;
    previousRuntime.current = runtime;
    if (previous !== null && previous !== runtime) previous.dispose();
  }, [runtime]);

  useEffect(() => {
    runtime?.setEventListener(onEvent);
  }, [runtime, onEvent]);

  const reference = respondent?.reference ?? null;
  useEffect(() => {
    runtime?.setRespondent(reference === null ? null : { reference });
  }, [runtime, reference]);

  const attributesKey = stableKey(attributes);
  useEffect(() => {
    runtime?.setAttributes(JSON.parse(attributesKey) as Attributes | null);
  }, [runtime, attributesKey]);

  const presentation = props.presentation ?? DEFAULT_PRESENTATION;
  const ui = useMemo<PitacoUiConfig>(
    () => ({
      ...(theme === undefined ? {} : { theme }),
      ...(strings === undefined ? {} : { strings }),
      ...(renderers === undefined ? {} : { renderers }),
      ...(slots === undefined ? {} : { slots }),
      presentation,
      ...(insets === undefined ? {} : { insets }),
      ...(getInsets === undefined ? {} : { getInsets }),
    }),
    [theme, strings, renderers, slots, presentation, insets, getInsets],
  );

  const value = useMemo<PitacoContextValue>(
    () => ({
      runtime,
      controller: runtime,
      reportRenderError: (error, context) =>
        runtime?.reportRenderError(error, context),
      ui,
    }),
    [runtime, ui],
  );

  return (
    <PitacoContext.Provider value={value}>
      {children}
      <PitacoErrorBoundary onError={value.reportRenderError}>
        <PitacoSurfaceHost />
      </PitacoErrorBoundary>
    </PitacoContext.Provider>
  );
}
