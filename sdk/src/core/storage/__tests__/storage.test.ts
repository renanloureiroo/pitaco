import { createAsyncStorageAdapter } from '../../../storage/async-storage';
import { createMmkvAdapter } from '../../../storage/mmkv';
import { createMemoryStorage, isMemoryStorage } from '../memory';
import { SafeStorage } from '../safe';

describe('adaptadores de armazenamento', () => {
  let warn: jest.SpyInstance;

  beforeEach(() => {
    warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => {
    warn.mockRestore();
  });

  it('AsyncStorage: usa a instância do app', async () => {
    const values = new Map<string, string>();
    const instance = {
      getItem: jest.fn((key: string) => Promise.resolve(values.get(key) ?? null)),
      setItem: jest.fn((key: string, value: string) => {
        values.set(key, value);
        return Promise.resolve();
      }),
      removeItem: jest.fn((key: string) => {
        values.delete(key);
        return Promise.resolve();
      }),
    };
    const adapter = createAsyncStorageAdapter(instance);
    await adapter.setItem('a', '1');
    expect(await adapter.getItem('a')).toBe('1');
    await adapter.removeItem('a');
    expect(await adapter.getItem('a')).toBeNull();
    expect(instance.setItem).toHaveBeenCalledWith('a', '1');
  });

  it('AsyncStorage: instância errada cai para memória e avisa como corrigir', () => {
    const adapter = createAsyncStorageAdapter({} as never);
    expect(isMemoryStorage(adapter)).toBe(true);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('createAsyncStorageAdapter(AsyncStorage)'));
  });

  it('MMKV 2 e 3: usa delete', () => {
    const values = new Map<string, string>();
    const instance = {
      getString: (key: string) => values.get(key),
      set: (key: string, value: string) => {
        values.set(key, value);
      },
      delete: jest.fn((key: string) => {
        values.delete(key);
      }),
    };
    const adapter = createMmkvAdapter(instance);
    void adapter.setItem('a', '1');
    expect(adapter.getItem('a')).toBe('1');
    void adapter.removeItem('a');
    expect(adapter.getItem('a')).toBeNull();
    expect(instance.delete).toHaveBeenCalledWith('a');
  });

  it('MMKV 4: usa remove', () => {
    const instance = { getString: () => undefined, set: () => undefined, remove: jest.fn(() => true) };
    void createMmkvAdapter(instance).removeItem('a');
    expect(instance.remove).toHaveBeenCalledWith('a');
  });

  it('MMKV: instância errada cai para memória e avisa', () => {
    expect(isMemoryStorage(createMmkvAdapter({ getString: () => undefined } as never))).toBe(true);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('createMmkvAdapter'));
  });
});

describe('armazenamento seguro', () => {
  it('falha do adaptador não escapa: a sessão segue com a cópia em memória', async () => {
    const errors: string[] = [];
    const storage = new SafeStorage(
      {
        getItem: () => {
          throw new Error('quebrado');
        },
        setItem: () => Promise.reject(new Error('quebrado')),
        removeItem: () => undefined,
      },
      (operation) => errors.push(operation),
    );
    expect(await storage.write('k', 'v')).toBe(false);
    expect(await storage.read('k')).toBe('v');
    expect(errors).toEqual(['write', 'read']);
  });

  it('gravações saem na ordem pedida: a última vence', async () => {
    const memory = createMemoryStorage();
    const delays = [30, 0];
    const storage = new SafeStorage(
      {
        getItem: (key) => memory.getItem(key),
        setItem: (key, value) =>
          new Promise<void>((resolve) => {
            setTimeout(() => {
              memory.setItem(key, value);
              resolve();
            }, delays.shift() ?? 0);
          }),
        removeItem: (key) => memory.removeItem(key),
      },
      () => undefined,
    );
    jest.useFakeTimers();
    const first = storage.write('k', 'antigo');
    const second = storage.write('k', 'novo');
    await jest.advanceTimersByTimeAsync(50);
    await Promise.all([first, second]);
    jest.useRealTimers();
    expect(memory.getItem('k')).toBe('novo');
  });

  it('JSON ilegível é tratado como ausente', async () => {
    const errors: string[] = [];
    const storage = new SafeStorage(createMemoryStorage({ k: '{' }), (operation) => errors.push(operation));
    expect(await storage.readJson('k')).toBeNull();
    expect(errors).toEqual(['parse']);
  });
});
