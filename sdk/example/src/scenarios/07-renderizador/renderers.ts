// Os renderizadores substituídos do cenário 7 (constante de módulo: comparada por referência). O NPS
// é do app; a avaliação lança erro de propósito e cai nas estrelas padrão. Os outros quatro tipos
// continuam os do SDK, na mesma pesquisa.
import type { PitacoRendererMap } from '@pitaco/react-native';
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';
import { AppNps } from './AppNps';
import { BrokenRating } from './BrokenRating';

export const APP_RENDERERS: Partial<PitacoRendererMap> = { nps: AppNps, rating: BrokenRating };

export const RENDERERS_CONFIG: ScenarioProviderConfig = { renderers: APP_RENDERERS };
