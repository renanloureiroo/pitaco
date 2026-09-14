import seedData from '../generated/seed-survey.json';

export interface SeedSurvey {
  readonly title: string;
  readonly triggerEvent: string;
  readonly surveyId: string;
  readonly schema: unknown;
}

function isSeedSurveyArray(value: unknown): value is SeedSurvey[] {
  if (!Array.isArray(value)) return false;
  return value.every(
    (record) =>
      typeof record === 'object' &&
      record !== null &&
      typeof (record as any).title === 'string' &&
      typeof (record as any).triggerEvent === 'string' &&
      typeof (record as any).surveyId === 'string' &&
      'schema' in record
  );
}

// `null` quando o arquivo não tem o formato esperado
export const seedSurveys: readonly SeedSurvey[] = isSeedSurveyArray(seedData) ? seedData : [];
export const seedSurvey: SeedSurvey | null = seedSurveys.length > 0 ? seedSurveys[0] : null;
