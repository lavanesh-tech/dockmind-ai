import { Component, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'dm-login',
  imports: [MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <div class="min-h-screen flex items-center justify-center p-6">
      <mat-card class="max-w-md w-full">
        <mat-card-header>
          <mat-icon mat-card-avatar>local_shipping</mat-icon>
          <mat-card-title>DockMind</mat-card-title>
          <mat-card-subtitle>Dock scheduling and disruption recovery</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content class="mt-4">
          <p>Sign in with your DockMind account. Demo users (synthetic data):</p>
          <ul class="muted text-sm list-disc pl-6">
            <li><b>admin.acme</b>: admin of tenant <i>acme</i></li>
            <li><b>dispatcher.acme</b>: dispatcher of <i>acme</i></li>
            <li><b>viewer.acme</b>: read-only</li>
            <li><b>dispatcher.globex</b>: a different company (can't see acme's data)</li>
          </ul>
          <p class="muted text-sm">The demo password is DEMO_USER_PASSWORD in your local .env file.</p>
        </mat-card-content>
        <mat-card-actions align="end">
          <button mat-flat-button (click)="auth.login()" data-testid="sign-in">Sign in</button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
})
export class LoginPage {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      if (this.auth.loggedIn()) {
        this.router.navigateByUrl('/dashboard');
      }
    });
  }
}
