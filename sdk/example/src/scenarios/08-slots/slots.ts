// Os slots substituídos do cenário 8 (constantes de módulo: comparadas por referência).
import type { PitacoSlotMap } from '@pitaco/react-native';
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';
import { BrandFooter } from './BrandFooter';
import { BrandHeader } from './BrandHeader';

export const BRAND_SLOTS: Partial<PitacoSlotMap> = { Header: BrandHeader, Footer: BrandFooter };

export const SLOTS_CONFIG: ScenarioProviderConfig = { slots: BRAND_SLOTS };
