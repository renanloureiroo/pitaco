// Isola um renderizador ou slot substituído: roda dentro do error boundary do Pitaco
// (`PitacoErrorBoundary`, da fase 2) e, se lançar, cai no componente padrão daquele tipo/slot com
// as mesmas props, avisa em `__DEV__` e emite o relatório de erro do SDK
// (`reportRenderError`, do contexto).
//
// Usado por `<PitacoSurveyContent />` para toda substituição de `renderers` e `slots`, do
// Provider ou local. 3b e 3c não precisam disto diretamente: os renderizadores e slots que eles
// escrevem são só componentes comuns; é `PitacoSurveyContent` quem os isola.

import { useContext, type ReactNode } from 'react';
import { createLogger } from '../../core/logger';
import { PitacoContext } from '../../react/context';
import { PitacoErrorBoundary } from '../../react/ErrorBoundary';

const logger = createLogger();

export type UiSubstitutionKind = 'renderer' | 'slot';

export interface SafeCustomProps<P extends object> {
  readonly kind: UiSubstitutionKind;
  // Nome técnico da substituição (a chave em `renderers`/`slots`), só para o log e o relatório.
  readonly name: string;
  // `undefined` quando não há substituição: renderiza direto o padrão, sem error boundary (o
  // padrão do próprio Pitaco não precisa de rede de segurança contra si mesmo).
  readonly custom: ((props: P) => ReactNode) | undefined;
  readonly Default: (props: P) => ReactNode;
  readonly props: P;
  // Identidade da instância do error boundary (por exemplo a chave da pergunta atual): ao mudar,
  // um boundary que já caiu no padrão tenta de novo o componente substituído, do zero.
  readonly resetKey?: string;
}

export function SafeCustom<P extends object>(options: SafeCustomProps<P>): ReactNode {
  const { kind, name, custom, Default, props, resetKey } = options;
  const context = useContext(PitacoContext);

  if (custom === undefined) return <Default {...props} />;

  const Custom = custom;
  const onError = (error: unknown) => {
    logger.warnOnce(
      `ui-${kind}-${name}`,
      `o ${kind === 'renderer' ? 'renderizador' : 'slot'} substituído "${name}" lançou um erro e caiu no padrão. Verifique o componente passado em ${
        kind === 'renderer' ? 'renderers' : 'slots'
      }.${name}.`,
    );
    context?.reportRenderError(error, { kind, name });
  };

  return (
    <PitacoErrorBoundary key={resetKey} fallback={<Default {...props} />} onError={onError}>
      <Custom {...props} />
    </PitacoErrorBoundary>
  );
}
