// TODO §90 (D7) — les tests s'exécutent en français (la langue d'origine et de repli) : jsdom annonce `en-US`, ce qui ferait
// basculer la détection sur l'anglais. Ce fichier passe AVANT setup.ts (les imports sont hoistés : il ne peut pas en faire partie).
// Les tests d'anglais choisissent explicitement leur langue (setLocale('en')).
Object.defineProperty(window.navigator, 'language', { value: 'fr-FR', configurable: true })
Object.defineProperty(window.navigator, 'languages', { value: ['fr-FR', 'fr'], configurable: true })
