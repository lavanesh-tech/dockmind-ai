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
import { CAPABILITIES, Dock } from '../core/models';

@Component({
  selector: 'dm-docks',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule,
    MatSnackBarModule],
  template: `
    <div class="page">
      <h1 class="text-2xl mb-4">Docks</h1>
      @if (auth.isAdmin()) {
        <form [formGroup]="form" (ngSubmit)="register()" class="tile mb-6 grid grid-cols-2 md:grid-cols-4 gap-x-4">
          <mat-form-field><mat-label>Code</mat-label><input matInput formControlName="code" placeholder="D01-REEFER" data-testid="dock-code"></mat-form-field>
          <mat-form-field><mat-label>Capabilities</mat-label>
            <mat-select formControlName="capabilities" multiple>@for (c of capabilities; track c) { <mat-option [value]="c">{{ c }}</mat-option> }</mat-select>
          </mat-form-field>
          <mat-form-field><mat-label>Opens (UTC)</mat-label><input matInput type="time" formControlName="opensAt"></mat-form-field>
          <mat-form-field><mat-label>Closes (UTC)</mat-label><input matInput type="time" formControlName="closesAt"></mat-form-field>
          <div class="col-span-2 md:col-span-4 flex justify-end">
            <button mat-flat-button type="submit" [disabled]="form.invalid" data-testid="add-dock">Add dock</button>
          </div>
        </form>
      } @else {
        <p class="muted mb-4">Only admins can add docks.</p>
      }
      <table mat-table [dataSource]="docks()" class="data">
        <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let d">{{ d.code }}</td></ng-container>
        <ng-container matColumnDef="capabilities"><th mat-header-cell *matHeaderCellDef>Capabilities</th><td mat-cell *matCellDef="let d">{{ d.capabilities.join(', ') || '—' }}</td></ng-container>
        <ng-container matColumnDef="hours"><th mat-header-cell *matHeaderCellDef>Hours (UTC)</th><td mat-cell *matCellDef="let d">{{ d.opensAt }}–{{ d.closesAt }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `,
})
export class DocksPage {
  private readonly api = inject(ApiService);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);
  protected readonly capabilities = CAPABILITIES;
  protected readonly columns = ['code', 'capabilities', 'hours'];
  protected readonly docks = signal<Dock[]>([]);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z0-9][A-Z0-9-]{0,31}$/)]],
    capabilities: [[] as (typeof CAPABILITIES)[number][]],
    opensAt: ['06:00', Validators.required],
    closesAt: ['22:00', Validators.required],
  });

  constructor() {
    this.refresh();
  }

  private refresh(): void {
    this.api.docks().subscribe({ next: d => this.docks.set(d), error: e => this.snack.open(errorMessage(e), 'OK') });
  }

  protected register(): void {
    this.api.registerDock(this.form.getRawValue()).subscribe({
      next: d => {
        this.snack.open(`Added ${d.code}`, 'OK', { duration: 3000 });
        this.form.controls.code.reset('');
        this.refresh();
      },
      error: e => this.snack.open(errorMessage(e), 'OK', { duration: 6000 }),
    });
  }
}
