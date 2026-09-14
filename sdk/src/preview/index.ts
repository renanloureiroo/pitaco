// `@pitaco/react-native/preview`
//
// A pesquisa rodando sobre um schema em memória: a mesma máquina de estado e os mesmos eventos,
// sem transporte, sem sessão de app e sem envio. Os eventos vão só para `onEvent`. Nada aqui (nem
// transitivamente) importa transporte (`core/transport/*`) ou armazenamento persistente
// (`core/storage/async-storage`/`mmkv`, `storage/*`) — só a máquina de estado, o schema e a UI.
//
// `createPreviewController`/`PitacoPreviewProvider` (`controller.ts`) dão o controlador cru e o
// Provider de contexto — quem quiser montar a própria UI por cima usa só isto, como o exemplo
// (fase 5) reabrindo localmente. `<PitacoPreview />` (`PitacoPreview.tsx`) é a UI padrão pronta:
// o mesmo `PitacoPreviewProvider` mais `<PitacoSurveyContent />`, sempre inline.

export {
  createPreviewController,
  PitacoPreviewProvider,
  type PitacoPreviewProviderProps,
  type PreviewController,
  type PreviewOptions,
} from './controller';
export { PitacoPreview, type PitacoPreviewProps } from './PitacoPreview';

export { usePitacoSurvey } from '../react/usePitacoSurvey';
export type { UsePitacoSurveyResult } from '../react/usePitacoSurvey';
