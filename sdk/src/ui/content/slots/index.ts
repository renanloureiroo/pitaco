import type { PitacoSlotMap } from '../../types';
import { DefaultCloseButton } from './DefaultCloseButton';
import { DefaultFooter } from './DefaultFooter';
import { DefaultHeader } from './DefaultHeader';
import { DefaultProgress } from './DefaultProgress';
import { DefaultThankYou } from './DefaultThankYou';

// Os slots padrão da moldura; substituíveis um a um por `slots={{ Header: Meu }}`.
export const DEFAULT_SLOTS: PitacoSlotMap = {
  Header: DefaultHeader,
  Progress: DefaultProgress,
  Footer: DefaultFooter,
  CloseButton: DefaultCloseButton,
  ThankYou: DefaultThankYou,
};

export { DefaultCloseButton, DefaultFooter, DefaultHeader, DefaultProgress, DefaultThankYou };
