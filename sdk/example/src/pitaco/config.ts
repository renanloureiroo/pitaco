// Perfis de conexão do exemplo (contrato fixo com a fase 5b — não mude os nomes das variáveis).
//
// - "direto": `EXPO_PUBLIC_PITACO_BASE_URL` + `EXPO_PUBLIC_PITACO_API_KEY`, indo direto ao backend.
// - "proxy": `EXPO_PUBLIC_PITACO_PROXY_BASE_URL` (mesma chave `EXPO_PUBLIC_PITACO_API_KEY`),
//   passando pelo gateway local de `sdk/example/proxy/` (ADR-0008).
//
// `sdk/example/.env.example` (da fase 5b) documenta as três variáveis; o script de seed escreve
// `sdk/example/.env` com a chave emitida. Sem `.env`, os campos ficam vazios e o `PitacoProvider`
// desliga sozinho (silêncio, sem derrubar o app) até alguém rodar o seed.

export type ConnectionProfileId = 'direto' | 'proxy';

export interface ConnectionProfile {
  readonly id: ConnectionProfileId;
  readonly label: string;
  readonly baseUrl: string;
  readonly available: boolean;
  readonly description: string;
}

const apiKey = process.env.EXPO_PUBLIC_PITACO_API_KEY ?? '';
const directBaseUrl = process.env.EXPO_PUBLIC_PITACO_BASE_URL ?? '';
const proxyBaseUrl = process.env.EXPO_PUBLIC_PITACO_PROXY_BASE_URL ?? '';

export const PITACO_API_KEY = apiKey;

export const CONNECTION_PROFILES: readonly ConnectionProfile[] = [
  {
    id: 'direto',
    label: 'Direto',
    baseUrl: directBaseUrl,
    available: directBaseUrl !== '' && apiKey !== '',
    description: 'EXPO_PUBLIC_PITACO_BASE_URL — vai direto ao backend do Pitaco.',
  },
  {
    id: 'proxy',
    label: 'Proxy',
    baseUrl: proxyBaseUrl,
    available: proxyBaseUrl !== '' && apiKey !== '',
    description: 'EXPO_PUBLIC_PITACO_PROXY_BASE_URL — passa pelo gateway local (sdk/example/proxy/).',
  },
];

export function defaultProfileId(): ConnectionProfileId {
  return CONNECTION_PROFILES.find((profile) => profile.available)?.id ?? 'direto';
}

export function profileById(id: ConnectionProfileId): ConnectionProfile {
  return CONNECTION_PROFILES.find((profile) => profile.id === id) ?? CONNECTION_PROFILES[0]!;
}
