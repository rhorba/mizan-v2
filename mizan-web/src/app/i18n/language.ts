import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

export type Lang = 'fr' | 'ar';

export const LANGS: readonly Lang[] = ['fr', 'ar'];
const STORAGE_KEY = 'mizan.lang';

/** UI language and text direction. Arabic switches the whole document to right-to-left. */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);

  readonly current = signal<Lang>('fr');
  readonly dir = computed(() => (this.current() === 'ar' ? 'rtl' : 'ltr'));
  readonly other = computed<Lang>(() => (this.current() === 'ar' ? 'fr' : 'ar'));

  /** Stored choice, else the browser's language, else French. */
  init(): void {
    this.use(
      this.stored() ??
        (this.document.defaultView?.navigator.language.startsWith('ar') ? 'ar' : 'fr'),
    );
  }

  toggle(): void {
    this.use(this.other());
  }

  use(lang: Lang): void {
    this.current.set(lang);
    this.transloco.setActiveLang(lang);
    const html = this.document.documentElement;
    html.lang = lang;
    html.dir = this.dir();
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // Storage can be unavailable (private mode); the choice then lasts for this visit only.
    }
  }

  private stored(): Lang | null {
    try {
      const value = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
      return LANGS.find((l) => l === value) ?? null;
    } catch {
      return null;
    }
  }
}
