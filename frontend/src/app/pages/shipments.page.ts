import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { CAPABILITIES, PRIORITIES, Shipment } from '../core/models';
import { at, hhmm, todayUtc } from '../core/time';

@Component({
  selector: 'dm-shipments',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule,
    MatSnackBarModule],
  template: `
    <div class="page">
      <h1 class="text-2xl mb-4">Shipments</h1>
      @if (auth.canDispatch()) {
        <form [formGroup]="form" (ngSubmit)="book()" class="tile mb-6 grid grid-cols-2 md:grid-cols-4 gap-x-4" data-testid="book-form">
          <mat-form-field><mat-label>Reference</mat-label><input matInput formControlName="reference" data-testid="ref"></mat-form-field>
          <mat-form-field><mat-label>Direction</mat-label>
            <mat-select formControlName="direction"><mat-option value="INBOUND">Inbound</mat-option><mat-option value="OUTBOUND">Outbound</mat-option></mat-select>
          </mat-form-field>
          <mat-form-field><mat-label>Pallets</mat-label><input matInput type="number" formControlName="palletCount"></mat-form-field>
          <mat-form-field><mat-label>Priority</mat-label>
            <mat-select formControlName="priority">@for (p of priorities; track p) { <mat-option [value]="p">{{ p }}</mat-option> }</mat-select>
          </mat-form-field>
          <mat-form-field><mat-label>Needs</mat-label>
            <mat-select formControlName="requiredCapabilities" multiple>@for (c of capabilities; track c) { <mat-option [value]="c">{{ c }}</mat-option> }</mat-select>
          </mat-form-field>
          <mat-form-field><mat-label>Day (UTC)</mat-label><input matInput type="date" formControlName="day"></mat-form-field>
          <mat-form-field><mat-label>Window from</mat-label><input matInput type="time" formControlName="from"></mat-form-field>
          <mat-form-field><mat-label>Window to</mat-label><input matInput type="time" formControlName="to"></mat-form-field>
          <div class="col-span-2 md:col-span-4 flex justify-end">
            <button mat-flat-button type="submit" [disabled]="form.invalid || busy()" data-testid="book">Book shipment</button>
          </div>
        </form>
      }
      <table mat-table [dataSource]="rows()" class="data" data-testid="shipments-table">
        <ng-container matColumnDef="reference"><th mat-header-cell *matHeaderCellDef>Reference</th><td mat-cell *matCellDef="let s">{{ s.reference }}</td></ng-container>
        <ng-container matColumnDef="window"><th mat-header-cell *matHeaderCellDef>Window (UTC)</th><td mat-cell *matCellDef="let s">{{ hhmm(s.windowStart) }}–{{ hhmm(s.windowEnd) }}</td></ng-container>
        <ng-container matColumnDef="priority"><th mat-header-cell *matHeaderCellDef>Priority</th><td mat-cell *matCellDef="let s">{{ s.priority }}</td></ng-container>
        <ng-container matColumnDef="pallets"><th mat-header-cell *matHeaderCellDef>Pallets</th><td mat-cell *matCellDef="let s">{{ s.palletCount }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let s">{{ s.status }}{{ s.late ? ' (late)' : '' }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let s">
            @if (auth.canDispatch() && s.status === 'BOOKED') {
              <button mat-button (click)="arrived(s)">Arrived now</button>
              <button mat-button (click)="cancel(s)">Cancel</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (rows().length === 0) { <p class="muted mt-4">No shipments for {{ form.value.day }}.</p> }
    </div>
  `,
})
export class ShipmentsPage {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);
  protected readonly capabilities = CAPABILITIES;
  protected readonly priorities = PRIORITIES;
  protected readonly columns = ['reference', 'window', 'priority', 'pallets', 'status', 'actions'];
  protected readonly rows = signal<Shipment[]>([]);
  protected readonly busy = signal(false);
  protected readonly hhmm = hhmm;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    reference: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._\/-]+$/), Validators.maxLength(64)]],
    direction: ['INBOUND' as 'INBOUND' | 'OUTBOUND'],
    palletCount: [20, [Validators.required, Validators.min(1), Validators.max(60)]],
    priority: ['STANDARD' as (typeof PRIORITIES)[number]],
    requiredCapabilities: [[] as (typeof CAPABILITIES)[number][]],
    day: [todayUtc(), Validators.required],
    from: ['08:00', Validators.required],
    to: ['10:00', Validators.required],
  });

  constructor() {
    this.refresh();
    this.form.controls.day.valueChanges.subscribe(() => this.refresh());
  }

  protected refresh(): void {
    const day = this.form.getRawValue().day;
    this.api.shipments(at(day, '00:00'), at(day, '23:59')).subscribe({
      next: s => this.rows.set(s),
      error: e => this.snack.open(errorMessage(e), 'OK', { duration: 5000 }),
    });
  }

  protected book(): void {
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.api.bookShipment({
      reference: v.reference, direction: v.direction, palletCount: v.palletCount, priority: v.priority,
      requiredCapabilities: v.requiredCapabilities, windowStart: at(v.day, v.from), windowEnd: at(v.day, v.to),
    }).subscribe({
      next: s => {
        this.busy.set(false);
        this.snack.open(`Booked ${s.reference}`, 'OK', { duration: 3000 });
        this.form.controls.reference.reset('');
        this.refresh();
      },
      error: e => {
        this.busy.set(false);
        this.snack.open(errorMessage(e), 'OK', { duration: 6000 });
      },
    });
  }

  protected arrived(s: Shipment): void {
    this.api.recordArrival(s.id, new Date().toISOString()).subscribe({
      next: () => this.refresh(), error: e => this.snack.open(errorMessage(e), 'OK', { duration: 5000 }),
    });
  }

  protected cancel(s: Shipment): void {
    this.api.cancelShipment(s.id).subscribe({
      next: () => this.refresh(), error: e => this.snack.open(errorMessage(e), 'OK', { duration: 5000 }),
    });
  }
}
