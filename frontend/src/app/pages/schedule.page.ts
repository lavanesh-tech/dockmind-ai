import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Appointment, Schedule } from '../core/models';
import { hhmm, todayUtc } from '../core/time';

/** Appointments grouped by dock and sorted by start time. Exported for unit tests. */
export function byDock(appointments: Appointment[]): { dock: string; items: Appointment[] }[] {
  const groups = new Map<string, Appointment[]>();
  for (const a of appointments) {
    groups.set(a.dockCode, [...(groups.get(a.dockCode) ?? []), a]);
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([dock, items]) => ({ dock, items: [...items].sort((x, y) => x.start.localeCompare(y.start)) }));
}

@Component({
  selector: 'dm-schedule',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSnackBarModule],
  template: `
    <div class="page">
      <div class="flex flex-wrap items-center gap-4 mb-4">
        <h1 class="text-2xl flex-1">Schedule</h1>
        <mat-form-field subscriptSizing="dynamic"><mat-label>Day (UTC)</mat-label>
          <input matInput type="date" [ngModel]="date()" (ngModelChange)="load($event)" data-testid="schedule-date"></mat-form-field>
        @if (auth.canDispatch()) {
          <mat-form-field subscriptSizing="dynamic" class="w-28"><mat-label>Crews</mat-label>
            <input matInput type="number" min="1" max="50" [(ngModel)]="crews"></mat-form-field>
          <button mat-flat-button (click)="compute()" [disabled]="busy()" data-testid="compute">Compute schedule</button>
        }
      </div>
      @if (schedule(); as s) {
        <p class="muted mb-4" data-testid="schedule-summary">{{ s.scheduled }} scheduled · {{ s.unscheduled }} not scheduled ·
          average wait {{ s.avgWaitMinutes }} min · computed by {{ s.computedBy }} ({{ s.algorithm }})</p>
        <div class="grid md:grid-cols-3 gap-4">
          @for (g of groups(); track g.dock) {
            <div class="tile">
              <div class="font-medium mb-2">{{ g.dock }}</div>
              @for (a of g.items; track a.shipmentId) {
                <div class="flex justify-between text-sm py-1 border-b border-black/10">
                  <span>{{ a.reference }}</span><span class="muted">{{ hhmm(a.start) }}–{{ hhmm(a.end) }}</span>
                </div>
              }
            </div>
          }
        </div>
        @if (s.notScheduled.length) {
          <h2 class="text-lg mt-6 mb-2">Not scheduled</h2>
          @for (n of s.notScheduled; track n.shipmentId) { <div class="text-sm">{{ n.reference }}: {{ n.reason }}</div> }
        }
      } @else {
        <p class="muted">No schedule for {{ date() }} yet.</p>
      }
    </div>
  `,
})
export class SchedulePage {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);
  protected readonly date = signal(todayUtc());
  protected readonly schedule = signal<Schedule | null>(null);
  protected readonly busy = signal(false);
  protected readonly groups = computed(() => byDock(this.schedule()?.appointments ?? []));
  protected readonly hhmm = hhmm;
  protected crews = 4;

  constructor() {
    this.load(this.date());
  }

  protected load(date: string): void {
    this.date.set(date);
    this.api.schedule(date).subscribe({ next: s => this.schedule.set(s), error: () => this.schedule.set(null) });
  }

  protected compute(): void {
    this.busy.set(true);
    this.api.computeSchedule(this.date(), this.crews).subscribe({
      next: s => { this.busy.set(false); this.schedule.set(s); },
      error: e => { this.busy.set(false); this.snack.open(errorMessage(e), 'OK', { duration: 6000 }); },
    });
  }
}
