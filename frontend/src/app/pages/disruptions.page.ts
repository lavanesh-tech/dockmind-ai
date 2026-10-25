import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Appointment, Disruption, Dock } from '../core/models';
import { at, hhmm, todayUtc } from '../core/time';

@Component({
  selector: 'dm-disruptions',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatButtonToggleModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatSnackBarModule],
  template: `
    <div class="page">
      <h1 class="text-2xl mb-4">Disruptions</h1>
      @if (auth.canDispatch()) {
        <form [formGroup]="form" (ngSubmit)="report()" class="tile mb-6">
          <div class="flex items-center gap-4 mb-4">
            <mat-button-toggle-group formControlName="type">
              <mat-button-toggle value="LATE_ARRIVAL" data-testid="type-late">Late truck</mat-button-toggle>
              <mat-button-toggle value="DOCK_OUTAGE" data-testid="type-outage">Dock outage</mat-button-toggle>
            </mat-button-toggle-group>
            <mat-form-field subscriptSizing="dynamic"><mat-label>Day (UTC)</mat-label>
              <input matInput type="date" formControlName="day"></mat-form-field>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-x-4">
            @if (form.value.type === 'LATE_ARRIVAL') {
              <mat-form-field class="col-span-2"><mat-label>Truck</mat-label>
                <mat-select formControlName="shipmentId" data-testid="truck">
                  @for (a of appointments(); track a.shipmentId) {
                    <mat-option [value]="a.shipmentId">{{ a.reference }} ({{ a.dockCode }} {{ hhmm(a.start) }})</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field><mat-label>New arrival (UTC)</mat-label><input matInput type="time" formControlName="newArrival"></mat-form-field>
            } @else {
              <mat-form-field><mat-label>Dock</mat-label>
                <mat-select formControlName="dockCode">@for (d of docks(); track d.id) { <mat-option [value]="d.code">{{ d.code }}</mat-option> }</mat-select>
              </mat-form-field>
              <mat-form-field><mat-label>Out from (UTC)</mat-label><input matInput type="time" formControlName="outageStart"></mat-form-field>
              <mat-form-field><mat-label>Out until (UTC)</mat-label><input matInput type="time" formControlName="outageEnd"></mat-form-field>
            }
            <mat-form-field><mat-label>Plan from (UTC)</mat-label><input matInput type="time" formControlName="decisionTime"></mat-form-field>
          </div>
          <div class="flex justify-end">
            <button mat-flat-button type="submit" [disabled]="busy()" data-testid="report">Get recovery plans</button>
          </div>
        </form>
      }
      @for (d of list(); track d.id) {
        <a [routerLink]="['/disruptions', d.id]" class="block tile mb-2 no-underline text-inherit">
          <b>{{ d.status }}</b> · {{ d.type === 'LATE_ARRIVAL' ? 'Late truck' : 'Dock ' + d.dockCode + ' out' }}
          on {{ d.scheduleDate }} · {{ d.plans.length }} plan(s) · reported by {{ d.reportedBy }} {{ d.reportedAt | date: 'short' : 'UTC' }}
        </a>
      } @empty { <p class="muted">No disruptions reported.</p> }
    </div>
  `,
})
export class DisruptionsPage {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  protected readonly list = signal<Disruption[]>([]);
  protected readonly docks = signal<Dock[]>([]);
  protected readonly appointments = signal<Appointment[]>([]);
  protected readonly busy = signal(false);
  protected readonly hhmm = hhmm;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    type: ['LATE_ARRIVAL' as 'LATE_ARRIVAL' | 'DOCK_OUTAGE'],
    day: [todayUtc(), Validators.required],
    shipmentId: [''],
    newArrival: ['10:00'],
    dockCode: [''],
    outageStart: ['10:00'],
    outageEnd: ['12:00'],
    decisionTime: [new Date().toISOString().slice(11, 16)],
  });

  constructor() {
    this.api.disruptions().subscribe({ next: d => this.list.set(d), error: e => this.snack.open(errorMessage(e), 'OK') });
    this.api.docks().subscribe({ next: d => this.docks.set(d), error: () => this.docks.set([]) });
    this.loadAppointments();
    this.form.controls.day.valueChanges.subscribe(() => this.loadAppointments());
  }

  private loadAppointments(): void {
    this.api.schedule(this.form.getRawValue().day).subscribe({
      next: s => this.appointments.set(s.appointments), error: () => this.appointments.set([]),
    });
  }

  protected report(): void {
    const v = this.form.getRawValue();
    const body: Record<string, unknown> = { type: v.type, scheduleDate: v.day, decisionTime: at(v.day, v.decisionTime) };
    if (v.type === 'LATE_ARRIVAL') {
      Object.assign(body, { shipmentId: v.shipmentId, newArrival: at(v.day, v.newArrival) });
    } else {
      Object.assign(body, { dockCode: v.dockCode, outageStart: at(v.day, v.outageStart), outageEnd: at(v.day, v.outageEnd) });
    }
    this.busy.set(true);
    this.api.reportDisruption(body).subscribe({
      next: d => { this.busy.set(false); this.router.navigate(['/disruptions', d.id]); },
      error: e => { this.busy.set(false); this.snack.open(errorMessage(e), 'OK', { duration: 6000 }); },
    });
  }
}
