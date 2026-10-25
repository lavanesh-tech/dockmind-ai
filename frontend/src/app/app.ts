import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatSidenavModule, MatListModule,
    MatIconModule, MatButtonModule, MatMenuModule],
  template: `
    @if (auth.identity(); as me) {
      <mat-toolbar class="gap-3">
        <mat-icon>local_shipping</mat-icon>
        <span class="font-medium">DockMind</span>
        <span class="flex-1"></span>
        <span class="muted text-sm" data-testid="who">{{ me.username }} · {{ me.tenant ?? 'no tenant' }} · {{ me.roles.join(', ') }}</span>
        <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Account"><mat-icon>account_circle</mat-icon></button>
        <mat-menu #menu="matMenu">
          <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon>Sign out</button>
        </mat-menu>
      </mat-toolbar>
      <mat-sidenav-container class="h-[calc(100vh-64px)]">
        <mat-sidenav mode="side" opened class="w-56">
          <mat-nav-list>
            @for (item of nav; track item.path) {
              <a mat-list-item [routerLink]="item.path" routerLinkActive #rla="routerLinkActive" [activated]="rla.isActive">
                <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
                <span matListItemTitle>{{ item.label }}</span>
              </a>
            }
          </mat-nav-list>
        </mat-sidenav>
        <mat-sidenav-content><router-outlet /></mat-sidenav-content>
      </mat-sidenav-container>
    } @else {
      <router-outlet />
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly nav = [
    { path: '/dashboard', icon: 'dashboard', label: 'Dashboard' },
    { path: '/shipments', icon: 'inventory_2', label: 'Shipments' },
    { path: '/schedule', icon: 'calendar_view_day', label: 'Schedule' },
    { path: '/disruptions', icon: 'warning', label: 'Disruptions' },
    { path: '/docks', icon: 'garage', label: 'Docks' },
    { path: '/assistant', icon: 'smart_toy', label: 'Assistant' },
  ];
}
