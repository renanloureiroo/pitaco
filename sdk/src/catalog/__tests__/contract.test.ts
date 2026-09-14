/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access, @typescript-eslint/no-unsafe-return */
import * as fs from 'fs';
import * as path from 'path';
import { INTERACTION_EVENT_TYPES } from '../events';

describe('Interaction Event Catalog Contract', () => {
  it('should match the backend InteractionEventType.java enum', () => {
    const javaPath = path.resolve(
      __dirname,
      '../../../../backend/src/main/java/com/renanloureiroo/pitaco/core/catalog/InteractionEventType.java'
    );
    
    expect(fs.existsSync(javaPath)).toBe(true);

    const javaContent = fs.readFileSync(javaPath, 'utf8');
    
    const enumMatches = javaContent.match(/([A-Z_]+)\(/g);
    expect(enumMatches).not.toBeNull();
    
    const backendEvents = enumMatches!
      .map((match: string) => match.replace('(', '').toLowerCase())
      .filter((name: string) => name !== 'catalog_version');

    const sdkEvents = [...INTERACTION_EVENT_TYPES];
    
    expect(sdkEvents.sort()).toEqual(backendEvents.sort());
  });
});
