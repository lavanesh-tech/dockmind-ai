import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { ApiService, errorMessage } from '../core/api.service';
import { Disruption, Schedule } from '../core/models';
import { todayUtc } from '../core/time';

/** Appointments per dock, for the chart. Exported for unit tests. */
export function appointmentsPerDock(schedule: Schedule | null): { labels: string[]; counts: number[] } {
  const counts = new Map<string, number>();
  for (const a of schedule?.appointments ?? []) {
    counts.set(a.dockCode, (counts.get(a.dockCode) ?? 0) + 1);
  }
  const labels = [...counts.keys()].sort();
  return { labels, counts: labels.map(l => counts.get(l)!) };
}

@Component({
  selector: 'dm-dashboard',
  imports: [DatePipe, FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatChipsModule, BaseChartDirective],
  template: `
    <div class="page">
      <div class="flex items-center gap-4 mb-4">
        <h1 class="text-2xl flex-1">Dashboard</h1>
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Day (UTC)</mat-label>
          <input matInput type="date" [ngModel]="date()" (ngModelChange)="load($event)" data-testid="dashboard-date">
        </mat-form-field>
      </div>
      @if (error()) { <p class="error-text">{{ error() }}</p> }
      @if (schedule(); as s) {
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div class="tile"><div class="muted">Scheduled trucks</div><div class="value" data-testid="kpi-scheduled">{{ s.scheduled }}</div></div>
          <div class="tile"><div class="muted">Not scheduled</div><div class="value">{{ s.unscheduled }}</div></div>
          <div class="tile"><div class="muted">Average wait</div><div class="value">{{ s.avgWaitMinutes }} min</div></div>
          <div class="tile"><div class="muted">Open disruptions</div><div class="value">{{ pending() }}</div></div>
        </div>
        <div class="tile mb-6">
          <div class="muted mb-2">Appointments per dock</div>
          <canvas baseChart type="bar" [data]="chart()" [options]="chartOptions" height="90"></canvas>
        </div>
      } @else if (!error()) {
        <p class="muted">No schedule computed for {{ date() }} yet. <a routerLink="/schedule">Compute one</a>.</p>
      }
      <h2 class="text-lg mb-2">Recent disruptions</h2>
      @for (d of disruptions().slice(0, 5); track d.id) {
        <a [routerLink]="['/disruptions', d.id]" class="block tile mb-2 no-underline text-inherit">
          <mat-chip-set><mat-chip>{{ d.status }}</mat-chip></mat-chip-set>
          {{ d.type === 'LATE_ARRIVAL' ? 'Late truck' : 'Dock ' + d.dockCode + ' out' }} on {{ d.scheduleDate }}
          · reported {{ d.reportedAt | date: 'short' : 'UTC' }}
        </a>
      } @empty {
        <p class="muted">None.</p>
      }
    </div>
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  protected readonly date = signal(todayUtc());
  protected readonly schedule = signal<Schedule | null>(null);
  protected readonly disruptions = signal<Disruption[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly pending = computed(() => this.disruptions().filter(d => d.status === 'PENDING_APPROVAL').length);
  protected readonly chart = computed<ChartConfiguration<'bar'>['data']>(() => {
    const { labels, counts } = appointmentsPerDock(this.schedule());
    return { labels, datasets: [{ data: counts, label: 'Appointments' }] };
  });
  protected readonly chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, aspectRatio: 4, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
  };

  constructor() {
    this.load(this.date());
    this.api.disruptions().subscribe({ next: d => this.disruptions.set(d), error: () => this.disruptions.set([]) });
  }

  protected load(date: string): void {
    this.date.set(date);
    this.error.set(null);
    this.api.schedule(date).subscribe({
      next: s => this.schedule.set(s),
      error: e => {
        this.schedule.set(null);
        if (e?.status !== 404) {
          this.error.set(errorMessage(e));
        }
      },
    });
  }
}
