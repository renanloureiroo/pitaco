import seedData from '../generated/seed-survey.json';

// `src/generated/seed-survey.json` é escrito por `npm run seed`: uma lista, na ordem do catálogo
// do script. A primeira é a que os cenários de pesquisa única usam.
export interface SeedSurvey {
  readonly title: string;
  readonly triggerEvent: string;
  readonly surveyId: string;
  readonly schema: unknown;
}

function asSeedSurvey(value: unknown): SeedSurvey | null {
  if (typeof value !== 'object' || value === null) return null;
  const record = value as Record<string, unknown>;
  if (
    typeof record.title !== 'string' ||
    typeof record.triggerEvent !== 'string' ||
    typeof record.surveyId !== 'string' ||
    !('schema' in record)
  ) {
    return null;
  }
  return record as unknown as SeedSurvey;
}

const data: unknown = seedData;

export const seedSurveys: readonly SeedSurvey[] = Array.isArray(data)
  ? data.map(asSeedSurvey).filter((survey): survey is SeedSurvey => survey !== null)
  : [];

export const seedSurvey: SeedSurvey | null = seedSurveys[0] ?? null;
