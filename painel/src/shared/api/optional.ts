import { z } from "zod";

export function absent<T extends z.ZodType>(schema: T) {
  return schema
    .nullish()
    .transform((value) => value ?? undefined)
    .optional();
}
