import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import ar from '../../public/i18n/ar.json';
import fr from '../../public/i18n/fr.json';

/** Real fr/ar files, loaded synchronously, so specs assert on the shipped copy. */
export function translocoTesting(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { fr, ar },
    translocoConfig: {
      availableLangs: ['fr', 'ar'],
      defaultLang: 'fr',
      reRenderOnLangChange: true,
    },
    preloadLangs: true,
    ...options,
  });
}
