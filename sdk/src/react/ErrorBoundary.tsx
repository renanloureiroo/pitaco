import { Component, type ReactNode } from 'react';

interface Props {
  readonly children?: ReactNode;
  readonly fallback?: ReactNode;
  readonly onError?: (error: unknown) => void;
}

interface State {
  readonly failed: boolean;
}

// Isola o que o SDK desenha: um erro de renderização do Pitaco vira silêncio (ou o `fallback`) e
// relatório de erro, nunca a árvore do app derrubada. Os renderizadores substituídos por tipo de
// pergunta ganham um destes cada, com o renderizador padrão como `fallback`.
export class PitacoErrorBoundary extends Component<Props, State> {
  override state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  override componentDidCatch(error: unknown): void {
    try {
      this.props.onError?.(error);
    } catch {
      // Quem reporta também não derruba nada.
    }
  }

  override render(): ReactNode {
    return this.state.failed ? (this.props.fallback ?? null) : (this.props.children ?? null);
  }
}
