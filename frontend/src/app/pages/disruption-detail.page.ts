import { Component, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AuditEntry, Disruption, PlanSummary } from '../core/models';

/** Whether the current user may approve this plan (the services check again). Exported for unit tests. */
export function mayApprove(plan: PlanSummary, canDispatch: boolean, isAdmin: boolean): boolean {
  return canDispatch && (!plan.requiresAdmin || isAdmin);
}

@Component({
  selector: 'dm-disruption-detail',
  imports: [DatePipe, FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, MatSnackBarModule],
  template: `
    <div class="page">
      <a routerLink="/disruptions" class="muted">← Disruptions</a>
      @if (d(); as d) {
        <h1 class="text-2xl my-2">{{ d.type === 'LATE_ARRIVAL' ? 'Late truck' : 'Dock ' + d.dockCode + ' outage' }} · {{ d.scheduleDate }}</h1>
        <p class="mb-4"><b data-testid="status">{{ d.status }}</b>
          @if (d.appliedStrategy) { · applied {{ d.appliedStrategy }} by {{ d.decidedBy }} }
          @if (d.status === 'REJECTED') { · rejected by {{ d.decidedBy }}: {{ d.decisionNote }} }
        </p>
        <h2 class="text-lg mb-2">Recovery plans</h2>
        <div class="grid md:grid-cols-2 gap-4 mb-6">
          @for (p of d.plans; track p.strategy) {
            <div class="tile" [class.ring-2]="p.recommended" [attr.data-testid]="'plan-' + p.strategy">
              <div class="flex justify-between mb-2">
                <b>{{ p.strategy }}</b>
                @if (p.recommended) { <span class="text-sm">Recommended</span> }
              </div>
              <div class="text-sm">Appointments moved: <b>{{ p.moved }}</b></div>
              <div class="text-sm">Trucks not scheduled: {{ p.unscheduled }} (urgent: {{ p.urgentUnscheduled }})</div>
              <div class="text-sm">Average wait: {{ p.avgWaitMinutes }} min</div>
              @if (p.requiresAdmin) { <div class="text-sm muted">Large change: needs an admin</div> }
              @if (d.status === 'PENDING_APPROVAL') {
                <div class="flex justify-end mt-2">
                  <button mat-flat-button [disabled]="!mayApprove(p) || busy()" (click)="approve(p)" [attr.data-testid]="'approve-' + p.strategy">Approve</button>
                </div>
              }
            </div>
          }
        </div>
        @if (d.status === 'PENDING_APPROVAL' && auth.canDispatch()) {
          <div class="flex items-center gap-4 mb-6">
            <mat-form-field class="flex-1" subscriptSizing="dynamic"><mat-label>Note (required to reject)</mat-label>
              <input matInput [(ngModel)]="note" maxlength="500"></mat-form-field>
            <button mat-stroked-button [disabled]="!note.trim() || busy()" (click)="reject()">Reject all</button>
          </div>
        }
        <h2 class="text-lg mb-2">Audit trail</h2>
        @for (e of audit(); track $index) {
          <div class="text-sm py-1 border-b border-black/10" data-testid="audit-row">
            <span class="muted">{{ e.at | date: 'medium' : 'UTC' }}</span> · <b>{{ e.action }}</b> · {{ e.actor }} · {{ e.detail }}
          </div>
        }
      } @else if (error()) {
        <p class="error-text">{{ error() }}</p>
      }
    </div>
  `,
})
export class DisruptionDetailPage {
  readonly id = input.required<string>();
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);
  protected readonly d = signal<Disruption | null>(null);
  protected readonly audit = signal<AuditEntry[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected note = '';

  constructor() {
    effect(() => this.load(this.id()));
  }

  protected mayApprove(p: PlanSummary): boolean {
    return mayApprove(p, this.auth.canDispatch(), this.auth.isAdmin());
  }

  private load(id: string): void {
    this.api.disruption(id).subscribe({ next: d => this.d.set(d), error: e => this.error.set(errorMessage(e)) });
    this.api.audit(id).subscribe({ next: a => this.audit.set(a), error: () => this.audit.set([]) });
  }

  protected approve(p: PlanSummary): void {
    this.busy.set(true);
    this.api.approve(this.id(), p.strategy, this.note.trim() || null).subscribe({
      next: d => { this.busy.set(false); this.d.set(d); this.load(this.id()); this.snack.open('Plan applied', 'OK', { duration: 3000 }); },
      error: e => { this.busy.set(false); this.load(this.id()); this.snack.open(errorMessage(e), 'OK', { duration: 8000 }); },
    });
  }

  protected reject(): void {
    this.busy.set(true);
    this.api.reject(this.id(), this.note.trim()).subscribe({
      next: d => { this.busy.set(false); this.d.set(d); this.load(this.id()); },
      error: e => { this.busy.set(false); this.snack.open(errorMessage(e), 'OK', { duration: 6000 }); },
    });
  }
}
