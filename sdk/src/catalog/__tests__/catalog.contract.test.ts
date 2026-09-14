// Teste de contrato do catálogo: o módulo tipado do SDK contra o snapshot do OpenAPI do backend
// (`openapi/pitaco.json`, atualizado por `npm run gen:api`). Falha se um tipo existir de um lado
// e não do outro, se a versão divergir, ou se o `data` de um tipo mudar.

import spec from '../../../openapi/pitaco.json';
import type { components } from '../../api/openapi';
import {
  CATALOG_VERSION,
  INTERACTION_EVENT_DATA_FIELDS,
  INTERACTION_EVENT_TYPES,
  type InteractionEventOf,
  type InteractionEventType,
  QUESTION_EVENT_TYPES,
} from '../events';
import { PLACEMENT_EVENT_TYPES } from '../placement';

type Schemas = components['schemas'];

interface SchemaObject {
  readonly enum?: readonly string[];
  readonly properties?: Readonly<Record<string, unknown>>;
  readonly required?: readonly string[];
  readonly discriminator?: { readonly mapping?: Readonly<Record<string, string>> };
  readonly 'x-pitaco-catalog-version'?: number;
}

const schemas = (spec as unknown as { components: { schemas: Record<string, SchemaObject> } }).components
  .schemas;

function schema(name: string): SchemaObject {
  const found = schemas[name];
  if (!found) throw new Error(`o OpenAPI não tem o esquema ${name}`);
  return found;
}

const pascal = (type: string) =>
  type
    .split('_')
    .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join('');

// Checagens de tipo: se o gerado e o SDK divergirem, o `npm run typecheck` falha aqui.
type Same<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
type Pascal<S extends string> = S extends `${infer Head}_${infer Rest}`
  ? `${Capitalize<Head>}${Pascal<Rest>}`
  : Capitalize<S>;
type MatchesContract = {
  readonly [T in InteractionEventType]: InteractionEventOf<T> extends Schemas[`${Pascal<T>}Event`] ? true : false;
};

const sameEnum: Same<InteractionEventType, Schemas['InteractionEventType']> = true;
const everyEventMatches: MatchesContract = {
  survey_presented: true,
  question_viewed: true,
  answer_selected: true,
  answer_changed: true,
  answer_deselected: true,
  text_focused: true,
  text_edited: true,
  text_blurred: true,
  validation_blocked: true,
  question_skipped: true,
  question_not_applicable: true,
  navigated_next: true,
  navigated_back: true,
  question_left: true,
  survey_backgrounded: true,
  survey_foregrounded: true,
  survey_dismissed: true,
  survey_completed: true,
};

describe('catálogo de eventos contra o OpenAPI do backend', () => {
  const contractTypes = schema('InteractionEventType').enum ?? [];

  it('os tipos são exatamente os mesmos, dos dois lados', () => {
    const sdk: string[] = [...INTERACTION_EVENT_TYPES].sort();
    const backend: string[] = [...contractTypes].sort();
    expect({ onlyInSdk: sdk.filter((type) => !backend.includes(type)) }).toEqual({ onlyInSdk: [] });
    expect({ onlyInBackend: backend.filter((type) => !sdk.includes(type)) }).toEqual({ onlyInBackend: [] });
    expect(sdk).toHaveLength(18);
    expect(sameEnum).toBe(true);
  });

  it('a versão do catálogo é a mesma', () => {
    expect(schema('InteractionEventType')['x-pitaco-catalog-version']).toBe(CATALOG_VERSION);
  });

  it('o data de cada tipo tem os mesmos campos', () => {
    for (const type of INTERACTION_EVENT_TYPES) {
      const data = schema(`${pascal(type)}Data`);
      expect({ type, fields: [...INTERACTION_EVENT_DATA_FIELDS[type]].sort() }).toEqual({
        type,
        fields: Object.keys(data.properties ?? {}).sort(),
      });
      expect({ type, required: [...(data.required ?? [])].sort() }).toEqual({
        type,
        required: [...INTERACTION_EVENT_DATA_FIELDS[type]].sort(),
      });
    }
    expect(Object.values(everyEventMatches).every(Boolean)).toBe(true);
  });

  it('os eventos de pergunta são os que o contrato obriga a levar questionKey', () => {
    const withQuestion = INTERACTION_EVENT_TYPES.filter((type) =>
      (schema(`${pascal(type)}Event`).required ?? []).includes('questionKey'),
    );
    expect([...withQuestion].sort()).toEqual([...QUESTION_EVENT_TYPES].sort());
  });

  it('a união discriminada do contrato mapeia todos os tipos', () => {
    const mapping = schema('InteractionEvent').discriminator?.mapping ?? {};
    expect(Object.keys(mapping).sort()).toEqual([...INTERACTION_EVENT_TYPES].sort());
  });

  it('os eventos de posicionamento ficam fora do catálogo enviado ao servidor', () => {
    for (const type of PLACEMENT_EVENT_TYPES) {
      expect(type.startsWith('placement_')).toBe(true);
      expect(contractTypes).not.toContain(type);
    }
  });
});
