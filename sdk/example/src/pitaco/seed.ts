import seedData from '../generated/seed-survey.json';

export interface SeedSurvey {
  readonly title?: string;
  readonly triggerEvent: string;
  readonly surveyId: string;
  readonly schema: unknown;
}

function asSeedSurvey(value: unknown): SeedSurvey | null {
  if (typeof value !== 'object' || value === null) return null;
  const record = value as Record<string, unknown>;
  if (typeof record.triggerEvent !== 'string' || typeof record.surveyId !== 'string' || !('schema' in record)) {
    return null;
  }
  return record as unknown as SeedSurvey;
}

function parseSeedData(data: unknown): SeedSurvey | null {
  // Formato objeto simples: { triggerEvent, surveyId, schema }
  const direct = asSeedSurvey(data);
  if (direct !== null) return direct;
  // Formato array legado: [{ title, triggerEvent, surveyId, schema }, ...]
  if (Array.isArray(data) && data.length > 0) return asSeedSurvey(data[0]);
  return null;
}

function parseSeedSurveys(data: unknown): readonly SeedSurvey[] {
  if (Array.isArray(data)) {
    return data.map(asSeedSurvey).filter((s): s is SeedSurvey => s !== null);
  }
  const single = asSeedSurvey(data);
  return single !== null ? [single] : [];
}

export const seedSurvey: SeedSurvey | null = parseSeedData(seedData);
export const seedSurveys: readonly SeedSurvey[] = parseSeedSurveys(seedData);
