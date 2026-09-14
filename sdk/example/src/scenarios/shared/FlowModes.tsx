// Alterna entre o fluxo real (o `track` de verdade, com exibição e resposta gravadas no backend) e
// o preview local (`LocalPreview`). O modo real é o padrão. testIDs `cenario-<NN>-modo-real` e
// `cenario-<NN>-modo-preview`.
import { type ReactNode, useState } from 'react';
import { Segmented } from '../../ui/Segmented';

type Mode = 'real' | 'preview';

const MODE_OPTIONS = [
  { value: 'real', label: 'Fluxo real' },
  { value: 'preview', label: 'Preview local' },
] as const;

export function FlowModes({ nn, real, preview }: { nn: string; real: ReactNode; preview: ReactNode }) {
  const [mode, setMode] = useState<Mode>('real');
  return (
    <>
      <Segmented testIDPrefix={`cenario-${nn}-modo`} options={MODE_OPTIONS} value={mode} onChange={setMode} />
      {mode === 'real' ? real : preview}
    </>
  );
}
