# Goal — outillage local Myrmacta sous Windows + Podman

Mettre en place et valider l'outillage local de Myrmacta sous Windows avec **Podman** (pas
Docker). Lire d'abord `docs/guides/GETTING-STARTED.md`, `.env.example`, `docker-compose.yml` et la skill
`myrmacta-conventions`.

## Contexte poste

- Windows 11, PowerShell, JDK 25, Node 24, Python 3.14 (pip disponible).
- Podman 5.8.3 installé dans `C:\Program Files\RedHat\Podman\` ; machine `podman-machine-default`
  (WSL) créée et démarrée. Si `podman` est introuvable dans le shell, recharger le PATH :
  `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')`
- `podman compose` a besoin d'un fournisseur (`docker-compose` ou `podman-compose`). Vérifier avec
  `podman compose version` ; s'il manque, l'installer au niveau utilisateur (sans droits admin) et
  le signaler dans le rapport.
- Le backend tourne sur l'hôte via `.\mvnw.cmd`, pas en conteneur.

## Étapes

1. **Podman** — `podman machine list`, `podman info`. Machine arrêtée → s'arrêter et me demander
   de la démarrer.
2. **Config** — créer `.env` depuis `.env.example` sans changer les valeurs par défaut sauf
   nécessité (justifier chaque écart). Charger les variables dans le shell PowerShell avant le
   backend (méthode de `docs/guides/GETTING-STARTED.md` §3).
3. **PostgreSQL** — `podman compose up -d`. Vérifier : postgres healthy, base `myrmacta`,
   schéma `tenant_dev`, rôle `myrmacta_tenant_dev` capable de se connecter avec le mot de passe
   de `.env` (script `docker/postgres/init`).
4. **Keycloak** — realm `myrmacta` importé ; client `mon-app` (secret + redirect URI
   `http://localhost:8080/*`) cohérent avec `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` ; utilisateur
   `demo` / `demo` avec des rôles de realm suffisants (au moins `TENANT_ADMIN`). Corriger
   `docker/keycloak/import/myrmacta-realm.json` si besoin puis réimporter (supprimer le volume
   `keycloak_data` si nécessaire).
5. **Tests backend** — `.\mvnw.cmd verify` complet (unitaires, `*IT`, ArchUnit, couverture).
   - Si le PostgreSQL embarqué zonky échoue sous Windows : diagnostiquer, puis repli
     `MYRMACTA_TEST_PG_URL` / `_USER` / `_PASSWORD` (`docs/guides/GETTING-STARTED.md` §17) sur le postgres
     Podman.
   - `ClamAvAttachmentScanIT` (Testcontainers) : tenter de faire pointer Testcontainers vers
     Podman (`DOCKER_HOST` sur le pipe Podman, Ryuk adapté). Si impossible, il doit s'ignorer
     proprement via son mécanisme existant — cause expliquée dans le rapport.
6. **Tests frontend** (`frontend/`) — `npm ci`, `npm run lint`, `npm run typecheck`,
   `npm run test`, puis `npx playwright install chromium` et `npm run test:e2e`.
7. **Pile complète** — frontend buildé et servi depuis `static/`, backend profil `local` via
   `.\mvnw.cmd spring-boot:run` en arrière-plan. Vérifier :
   `http://127.0.0.1:8090/actuator/health` → `UP` ; `http://localhost:8080/api/auth/me` sans
   session → redirection vers Keycloak.
8. **Connexion de bout en bout** — dans le navigateur intégré : ouvrir `http://localhost:8080`,
   se connecter sur Keycloak avec le compte de test `demo` / `demo`, revenir authentifié.
   Prouver `/api/auth/me` → 200 avec l'utilisateur `demo` et ses rôles, et l'affichage de l'écran
   d'accueil (capture d'écran).

## Règles

- Corriger les causes racines. Interdit : `@Disabled`, `-DskipTests`, exclusions de tests,
  assertions affaiblies, sécurité affaiblie (D-41 / D-42 / D-43, en-têtes, cookie).
- Ne jamais modifier un changeSet Liquibase déjà appliqué.
- `docker-compose.yml` doit rester compatible Docker **et** Podman.
- Documenter les spécificités Windows/Podman dans `docs/guides/GETTING-STARTED.md` ; entrée `CHANGELOG.md`
  `[Non publié]` si du code ou de la config change.
- Branche `chore/outillage-podman-windows`, commits conventionnels. **Ne pas pousser** sans mon
  accord.
- Blocage nécessitant une action de ma part (droits admin, installation système) → s'arrêter et
  me le dire, ne pas contourner.

## Condition d'arrêt (sortie fraîche pour chaque point)

- (a) `.\mvnw.cmd verify` → `BUILD SUCCESS`
- (b) lint, typecheck, Vitest et Playwright au vert
- (c) postgres + keycloak démarrés sous Podman, config de base vérifiée (schéma `tenant_dev`,
  rôle applicatif, realm `myrmacta`, client `mon-app`, utilisateur `demo` avec ses rôles)
- (d) backend démarré, health `UP`
- (e) connexion à Myrmacta réussie avec `demo` / `demo`, `/api/auth/me` → 200

## Rapport final

Commandes exécutées, résultats (nombre de tests), fichiers modifiés, écarts et limites restantes.
