import { AuthConfig } from 'angular-oauth2-oidc';

/**
 * OpenID Connect login with Keycloak: authorization code flow with PKCE (S256).
 * The browser never sees a client secret; the access token is kept in session storage and
 * sent only to this app's own /api (see app.config.ts).
 */
export const authConfig: AuthConfig = {
  issuer: 'http://localhost:8180/realms/dockmind',
  clientId: 'dockmind-web',
  responseType: 'code',
  redirectUri: window.location.origin + '/',
  postLogoutRedirectUri: window.location.origin + '/',
  scope: 'openid profile email',
  requireHttps: 'remoteOnly',
  showDebugInformation: false,
  clearHashAfterLogin: true,
};
