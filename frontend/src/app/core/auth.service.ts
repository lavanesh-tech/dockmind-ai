import { Injectable, computed, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';
import { Identity, canDispatch, decodeJwtPayload, identityFromClaims, isAdmin } from './roles';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly token = signal<string | null>(null);

  readonly identity = computed<Identity | null>(() => identityFromClaims(decodeJwtPayload(this.token())));
  readonly loggedIn = computed(() => this.identity() !== null);
  readonly canDispatch = computed(() => canDispatch(this.identity()));
  readonly isAdmin = computed(() => isAdmin(this.identity()));

  /** Called once at startup: finishes a login redirect if there is one, and keeps tokens fresh. */
  async init(): Promise<void> {
    this.oauth.configure(authConfig);
    this.oauth.setupAutomaticSilentRefresh({}, 'access_token');
    this.oauth.events.subscribe(() => this.sync());
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
    } catch {
      // Login server unreachable: the login page explains it.
    }
    this.sync();
  }

  login(): void {
    this.oauth.initCodeFlow();
  }

  logout(): void {
    this.oauth.logOut();
    this.token.set(null);
  }

  private sync(): void {
    this.token.set(this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null);
  }
}
