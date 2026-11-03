// As seis rotas públicas do contrato (`backend/docs/backend/contrato-sdk.md`). O SDK acrescenta
// `/collect/...` ao `baseUrl` e não distingue acesso direto de gateway.

import type {
  EligibilityRequest,
  OpenDisplayRequest,
  SdkErrorReportRequest,
  SubmissionRequest,
  SuppressionRequest,
} from '../../api/types';
import type { InteractionEvent } from '../../catalog/events';
import type { HttpClient, HttpOutcome } from './http';

export type CollectRoute =
  | 'eligibility'
  | 'displays'
  | 'submission'
  | 'events'
  | 'suppressions'
  | 'sdk-errors';

export class CollectApi {
  constructor(
    private readonly http: HttpClient,
    private readonly eligibilityTimeoutMs: number,
  ) {}

  eligibility(body: EligibilityRequest): Promise<HttpOutcome> {
    return this.http.post('/collect/eligibility', body, {
      timeoutMs: this.eligibilityTimeoutMs,
      expectJson: true,
    });
  }

  openDisplay(body: OpenDisplayRequest): Promise<HttpOutcome> {
    return this.http.post('/collect/displays', body);
  }

  submit(displayId: string, body: SubmissionRequest): Promise<HttpOutcome> {
    return this.http.post(`/collect/displays/${encodeURIComponent(displayId)}/submission`, body);
  }

  sendEvents(displayId: string, events: readonly InteractionEvent[]): Promise<HttpOutcome> {
    return this.http.post(`/collect/displays/${encodeURIComponent(displayId)}/events`, { events });
  }

  suppress(body: SuppressionRequest): Promise<HttpOutcome> {
    return this.http.post('/collect/suppressions', body);
  }

  reportError(body: SdkErrorReportRequest): Promise<HttpOutcome> {
    return this.http.post('/collect/sdk-errors', body);
  }
}
