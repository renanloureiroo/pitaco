// A pesquisa do seed (fase 5b), gerada em `src/generated/seed-survey.json` pelo script de seed
// contra o backend local. `triggerEvent` é o evento que dispara a pesquisa pelo fluxo real
// (`track`); `schema` alimenta o `<PitacoPreview />` (reabertura local, cenário 4).
import seedData from '../generated/seed-survey.json';

export interface SeedSurvey {
  readonly triggerEvent: string;
  readonly surveyId: string;
  readonly schema: unknown;
}

function isSeedSurvey(value: unknown): value is SeedSurvey {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.triggerEvent === 'string' && typeof record.surveyId === 'string' && 'schema' in record;
}

// `null` quando o arquivo não tem o formato esperado: as telas mostram "rode o seed" em vez de
// quebrar.
export const seedSurvey: SeedSurvey | null = isSeedSurvey(seedData) ? seedData : null;
