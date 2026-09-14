import { DEVICE_ID_KEY, DeviceIdentity } from '../identity';
import { createMemoryStorage } from '../storage/memory';
import { SafeStorage } from '../storage/safe';
import { createUuidGenerator, isUuidV4 } from '../uuid';

const safe = (storage = createMemoryStorage()) => new SafeStorage(storage, () => undefined);
const uuid = createUuidGenerator({ getCrypto: () => undefined });

describe('identidade do dispositivo', () => {
  it('gera o deviceId uma vez e persiste', async () => {
    const storage = createMemoryStorage();
    const first = await new DeviceIdentity(safe(storage), uuid).get();
    expect(isUuidV4(first)).toBe(true);
    expect(storage.getItem(DEVICE_ID_KEY)).toBe(first);

    const afterRestart = await new DeviceIdentity(safe(storage), uuid).get();
    expect(afterRestart).toBe(first);
  });

  it('chamadas simultâneas recebem o mesmo valor', async () => {
    const identity = new DeviceIdentity(safe(), uuid);
    const [a, b, c] = await Promise.all([identity.get(), identity.get(), identity.get()]);
    expect(new Set([a, b, c]).size).toBe(1);
  });

  it('valor salvo que não é UUID é trocado', async () => {
    const storage = createMemoryStorage({ [DEVICE_ID_KEY]: 'usuario@exemplo.com' });
    const value = await new DeviceIdentity(safe(storage), uuid).get();
    expect(isUuidV4(value)).toBe(true);
    expect(storage.getItem(DEVICE_ID_KEY)).toBe(value);
  });

  it('rotate (logout) gera outro e persiste', async () => {
    const storage = createMemoryStorage();
    const identity = new DeviceIdentity(safe(storage), uuid);
    const before = await identity.get();
    const after = await identity.rotate();
    expect(after).not.toBe(before);
    expect(await identity.get()).toBe(after);
    expect(storage.getItem(DEVICE_ID_KEY)).toBe(after);
  });

  it('com o armazenamento quebrado, a identidade vale pelo menos para a sessão', async () => {
    const errors: string[] = [];
    const broken = new SafeStorage(
      {
        getItem: () => Promise.reject(new Error('disco cheio')),
        setItem: () => Promise.reject(new Error('disco cheio')),
        removeItem: () => Promise.reject(new Error('disco cheio')),
      },
      (operation) => errors.push(operation),
    );
    const identity = new DeviceIdentity(broken, uuid);
    const value = await identity.get();
    expect(isUuidV4(value)).toBe(true);
    expect(await identity.get()).toBe(value);
    expect(errors).toEqual(['read', 'write']);
  });
});
