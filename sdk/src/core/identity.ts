// `deviceId`: UUID gerado uma vez por instalação e persistido. É o respondente quando o app não
// passa `respondent.reference`, e vai sempre junto na elegibilidade, como o contrato pede.
// `reset()` (logout) gera outro, para o próximo usuário do mesmo aparelho não herdar o histórico.

import type { SafeStorage } from './storage/safe';
import { isUuid } from './uuid';

export const DEVICE_ID_KEY = '@pitaco/v1:device-id';

export class DeviceIdentity {
  private value: string | null = null;
  private loading: Promise<string> | null = null;

  constructor(
    private readonly storage: SafeStorage,
    private readonly uuid: () => string,
  ) {}

  get current(): string | null {
    return this.value;
  }

  get(): Promise<string> {
    if (this.value !== null) return Promise.resolve(this.value);
    if (this.loading === null) this.loading = this.load();
    return this.loading;
  }

  async rotate(): Promise<string> {
    const fresh = this.uuid();
    this.value = fresh;
    this.loading = Promise.resolve(fresh);
    await this.storage.write(DEVICE_ID_KEY, fresh);
    return fresh;
  }

  private async load(): Promise<string> {
    const stored = await this.storage.read(DEVICE_ID_KEY);
    if (this.value !== null) return this.value;
    if (isUuid(stored)) {
      this.value = stored.toLowerCase();
      return this.value;
    }
    const fresh = this.uuid();
    this.value = fresh;
    await this.storage.write(DEVICE_ID_KEY, fresh);
    return fresh;
  }
}
