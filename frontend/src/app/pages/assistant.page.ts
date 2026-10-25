import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService, errorMessage } from '../core/api.service';

interface Turn {
  question: string;
  answer?: string;
  tools?: string[];
  notice?: string;
  error?: string;
}

@Component({
  selector: 'dm-assistant',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  template: `
    <div class="page max-w-3xl">
      <h1 class="text-2xl mb-1">Operations assistant</h1>
      <p class="muted mb-4">Ask about schedules, docks and disruptions. It can only read; it never changes anything.</p>
      @for (t of turns(); track $index) {
        <div class="mb-4">
          <div class="font-medium">You: {{ t.question }}</div>
          @if (t.answer !== undefined) {
            <div class="tile mt-1 whitespace-pre-wrap" data-testid="answer">{{ t.answer }}</div>
            <div class="text-xs muted mt-1">Looked up: {{ t.tools?.length ? t.tools?.join(', ') : 'nothing' }} · {{ t.notice }}</div>
          } @else if (t.error) {
            <div class="error-text mt-1">{{ t.error }}</div>
          }
        </div>
      }
      @if (busy()) { <mat-progress-bar mode="indeterminate" class="mb-4" /> }
      <form (ngSubmit)="ask()" class="flex gap-2 items-start">
        <mat-form-field class="flex-1" subscriptSizing="dynamic"><mat-label>Question</mat-label>
          <input matInput name="q" [(ngModel)]="question" maxlength="1000" placeholder="Which trucks are not scheduled today?" data-testid="question">
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!question.trim() || busy()">Ask</button>
      </form>
    </div>
  `,
})
export class AssistantPage {
  private readonly api = inject(ApiService);
  protected readonly turns = signal<Turn[]>([]);
  protected readonly busy = signal(false);
  protected question = '';

  protected ask(): void {
    const question = this.question.trim();
    const index = this.turns().length;
    this.turns.update(t => [...t, { question }]);
    this.question = '';
    this.busy.set(true);
    this.api.ask(question).subscribe({
      next: r => this.update(index, { answer: r.answer, tools: r.toolsUsed, notice: r.notice }),
      error: e => this.update(index, { error: errorMessage(e) }),
    });
  }

  private update(index: number, patch: Partial<Turn>): void {
    this.busy.set(false);
    this.turns.update(t => t.map((turn, i) => (i === index ? { ...turn, ...patch } : turn)));
  }
}
