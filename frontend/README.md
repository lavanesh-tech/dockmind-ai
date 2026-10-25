# DockMind web app

Angular 22 + Angular Material + Tailwind. Signs in with Keycloak (OIDC code flow with PKCE) and
talks only to the API gateway, through the dev server's `/api` proxy (`proxy.conf.json`).

```bash
npm ci
npm start          # http://localhost:4200 (needs the stack: ../scripts/run-local.sh)
npm test           # unit tests (Vitest)
npm run build      # production build
npm run e2e        # end-to-end workflow against the running stack (../scripts/run-local.sh e2e)
```
