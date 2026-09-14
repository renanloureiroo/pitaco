// Registro da execução do cenário 13 que sobrevive ao fechamento do app: qual exibição teve a
// resposta gravada na fila sem rede. Depois de reabrir, a tela confere por ele que o item saiu da
// fila e que o backend recebeu a resposta. Fica fora do prefixo `@pitaco/` de propósito: "Nova
// identidade" apaga só as chaves do SDK.
import AsyncStorage from '@react-native-async-storage/async-storage';

const KEY = '@pitaco-example/13-sem-rede';

export interface OfflineRun {
  readonly displayId: string;
  readonly surveyId: string;
  readonly queuedAt: string;
}

function isOfflineRun(value: unknown): value is OfflineRun {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.displayId === 'string' && typeof record.surveyId === 'string' && typeof record.queuedAt === 'string';
}

export async function loadOfflineRun(): Promise<OfflineRun | null> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    const parsed: unknown = raw === null ? null : JSON.parse(raw);
    return isOfflineRun(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export async function saveOfflineRun(run: OfflineRun): Promise<void> {
  try {
    await AsyncStorage.setItem(KEY, JSON.stringify(run));
  } catch {
    // Só o registro do exemplo; a fila do SDK não depende dele.
  }
}

export async function clearOfflineRun(): Promise<void> {
  try {
    await AsyncStorage.removeItem(KEY);
  } catch {
    // Idem.
  }
}
