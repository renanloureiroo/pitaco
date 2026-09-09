import { request, requestNoContent, type Result } from "@/shared/api";

import {
  segmentationRuleSchema,
  triggerSchema,
  type SegmentationRule,
  type SegmentationRuleForm,
  type Trigger,
  type TriggerForm,
} from "../schemas/trigger";
import { surveyPath } from "./paths";

function triggerPath(applicationId: string, surveyId: string): string {
  return `${surveyPath(applicationId, surveyId)}/trigger`;
}

/** `PUT` é idempotente por definição: redefinir **substitui**, não duplica (FR-025). */
export function defineTrigger(
  applicationId: string,
  surveyId: string,
  form: TriggerForm,
): Promise<Result<Trigger>> {
  return request(triggerSchema, {
    path: triggerPath(applicationId, surveyId),
    method: "PUT",
    body: form,
  });
}

export function addSegmentationRule(
  applicationId: string,
  surveyId: string,
  rule: SegmentationRuleForm,
): Promise<Result<SegmentationRule>> {
  return request(segmentationRuleSchema, {
    path: `${triggerPath(applicationId, surveyId)}/rules`,
    method: "POST",
    body: rule,
  });
}

export function removeSegmentationRule(
  applicationId: string,
  surveyId: string,
  ruleId: string,
): Promise<Result<void>> {
  return requestNoContent({
    path: `${triggerPath(applicationId, surveyId)}/rules/${encodeURIComponent(ruleId)}`,
    method: "DELETE",
  });
}
