import { render } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';
import { resolvePitacoTheme } from '../../../theme/useTheme';
import { DEFAULT_STRINGS } from '../../../strings/strings';
import { DefaultThankYou } from '../DefaultThankYou';

describe('DefaultThankYou (acessibilidade)', () => {
  it('anuncia o título e o corpo ao leitor de tela quando aparece', async () => {
    const announce = jest.spyOn(AccessibilityInfo, 'announceForAccessibility').mockImplementation(() => undefined);
    await render(
      <DefaultThankYou
        theme={resolvePitacoTheme(undefined, 'light')}
        strings={DEFAULT_STRINGS}
        durationMs={2500}
        onDone={() => undefined}
      />,
    );
    expect(announce).toHaveBeenCalledWith(`${DEFAULT_STRINGS.thankYouTitle}. ${DEFAULT_STRINGS.thankYouBody}`);
    announce.mockRestore();
  });
});
