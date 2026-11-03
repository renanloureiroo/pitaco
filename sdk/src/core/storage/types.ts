// O armazenamento que o SDK usa para a identidade do dispositivo e a fila local. É a interface
// mínima que o AsyncStorage e o MMKV satisfazem com um adaptador fino. O SDK nunca importa o
// pacote nativo: o app passa a própria instância.
export interface PitacoStorage {
  getItem(key: string): Promise<string | null | undefined> | string | null | undefined;
  setItem(key: string, value: string): Promise<void> | void;
  removeItem(key: string): Promise<void> | void;
}
