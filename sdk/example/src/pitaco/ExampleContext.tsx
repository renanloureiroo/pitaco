// Estado do exemplo (não faz parte do SDK): perfil de conexão, storage do Provider da raiz e a
// pilha de cenários montados, cada um com a configuração que quer dar ao Provider.
//
// Dois contextos: as ações são estáveis e as telas de cenário só consomem as ações, então trocar a
// configuração do Provider não re-renderiza a tela que pediu a troca.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createMemoryStorage, type PitacoProviderProps, type PitacoStorage } from '@pitaco/react-native';
import { createAsyncStorageAdapter } from '@pitaco/react-native/storage/async-storage';
import { createContext, type ReactNode, useCallback, useContext, useMemo, useState } from 'react';
import { LogBox } from 'react-native';
import { eventLog } from '../debug/eventLog';
import { networkLog } from '../debug/networkLog';
import { CONNECTION_PROFILES, type ConnectionProfile, type ConnectionProfileId, defaultProfileId, profileById } from './config';

// "Limpar storage e identidade" troca de propósito o storage por um em memória por 300 ms, e o SDK
// avisa em desenvolvimento que a fila não é persistente. O aviso continua no console do Metro; sem
// isto, o LogBox abre um aviso sobre a barra de abas (no Android, cobre "Cenários" e engole o toque).
// Não muda nada em produção.
LogBox.ignoreLogs(['Sem storage persistente']);

// O que um cenário pode variar no `<PitacoProvider>` da raiz. `onEvent` e `storage` ficam com o
// exemplo (painel de depuração e "instalação nova"); `baseUrl`/`apiKey` podem ser trocados (cenário
// 14, chave inválida e endereço inalcançável).
export type ScenarioProviderConfig = Partial<Omit<PitacoProviderProps, 'children' | 'onEvent' | 'storage'>>;

export interface ScenarioEntry {
  readonly token: string;
  readonly scenarioId: string;
  readonly config: ScenarioProviderConfig;
}

interface ExampleState {
  readonly profile: ConnectionProfile;
  readonly profiles: readonly ConnectionProfile[];
  readonly storage: PitacoStorage;
  // O cenário montado por último (o do topo da navegação); `null` na tela inicial.
  readonly activeScenario: ScenarioEntry | null;
}

interface ExampleActions {
  readonly setProfileId: (id: ConnectionProfileId) => void;
  readonly mountScenario: (token: string, scenarioId: string) => () => void;
  readonly configureScenario: (token: string, config: ScenarioProviderConfig) => void;
  readonly requestFreshInstall: () => Promise<void>;
}

const StateContext = createContext<ExampleState | null>(null);
const ActionsContext = createContext<ExampleActions | null>(null);

const STORAGE_PREFIX = '@pitaco/';

const createPersistentStorage = () => createAsyncStorageAdapter(AsyncStorage);
const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function sameConfig(a: ScenarioProviderConfig, b: ScenarioProviderConfig): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]) as Set<keyof ScenarioProviderConfig>;
  for (const key of keys) {
    if (!Object.is(a[key], b[key])) return false;
  }
  return true;
}

export function ExampleProvider({ children }: { children: ReactNode }): ReactNode {
  const [profileId, setProfileId] = useState<ConnectionProfileId>(defaultProfileId);
  const [storage, setStorage] = useState<PitacoStorage>(createPersistentStorage);
  const [stack, setStack] = useState<readonly ScenarioEntry[]>([]);

  const mountScenario = useCallback((token: string, scenarioId: string) => {
    setStack((current) => [...current.filter((entry) => entry.token !== token), { token, scenarioId, config: {} }]);
    return () => setStack((current) => current.filter((entry) => entry.token !== token));
  }, []);

  const configureScenario = useCallback((token: string, config: ScenarioProviderConfig) => {
    setStack((current) => {
      const index = current.findIndex((entry) => entry.token === token);
      const entry = current[index];
      if (entry === undefined || sameConfig(entry.config, config)) return current;
      const next = [...current];
      next[index] = { ...entry, config };
      return next;
    });
  }, []);

  // "Instalação nova": troca o storage do Provider por um em memória (o runtime atual se desliga e
  // pausa a fila), apaga as chaves do Pitaco no AsyncStorage e volta para um adaptador novo. Trocar
  // a instância de `storage` faz o `<PitacoProvider>` recriar o runtime sem remontar o app.
  const requestFreshInstall = useCallback(async () => {
    setStorage(createMemoryStorage());
    await wait(300);
    try {
      const keys = await AsyncStorage.getAllKeys();
      const ours = keys.filter((key) => key.startsWith(STORAGE_PREFIX));
      if (ours.length > 0) await AsyncStorage.multiRemove(ours);
    } finally {
      eventLog.clear();
      networkLog.clear();
      setStorage(createPersistentStorage());
    }
  }, []);

  const actions = useMemo<ExampleActions>(
    () => ({ setProfileId, mountScenario, configureScenario, requestFreshInstall }),
    [mountScenario, configureScenario, requestFreshInstall],
  );

  const state = useMemo<ExampleState>(
    () => ({
      profile: profileById(profileId),
      profiles: CONNECTION_PROFILES,
      storage,
      activeScenario: stack[stack.length - 1] ?? null,
    }),
    [profileId, storage, stack],
  );

  return (
    <ActionsContext.Provider value={actions}>
      <StateContext.Provider value={state}>{children}</StateContext.Provider>
    </ActionsContext.Provider>
  );
}

export function useExampleState(): ExampleState {
  const value = useContext(StateContext);
  if (value === null) throw new Error('useExampleState() precisa de <ExampleProvider> (app/_layout.tsx).');
  return value;
}

export function useExampleActions(): ExampleActions {
  const value = useContext(ActionsContext);
  if (value === null) throw new Error('useExampleActions() precisa de <ExampleProvider> (app/_layout.tsx).');
  return value;
}
