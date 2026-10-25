import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AskResponse, AuditEntry, Capability, Disruption, Dock, NewShipment, Problem, Schedule, Shipment,
} from './models';

/** All calls go to this app's own /api, which the dev server proxies to the API gateway. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  shipments(from: string, to: string): Observable<Shipment[]> {
    return this.http.get<Shipment[]>('/api/v1/shipments', { params: { from, to } });
  }

  bookShipment(body: NewShipment): Observable<Shipment> {
    return this.http.post<Shipment>('/api/v1/shipments', body);
  }

  recordArrival(id: string, arrivedAt: string): Observable<Shipment> {
    return this.http.post<Shipment>(`/api/v1/shipments/${id}/arrival`, { arrivedAt });
  }

  cancelShipment(id: string): Observable<Shipment> {
    return this.http.post<Shipment>(`/api/v1/shipments/${id}/cancel`, {});
  }

  docks(): Observable<Dock[]> {
    return this.http.get<Dock[]>('/api/v1/docks');
  }

  registerDock(body: { code: string; capabilities: Capability[]; opensAt: string; closesAt: string }): Observable<Dock> {
    return this.http.post<Dock>('/api/v1/docks', body);
  }

  schedule(date: string): Observable<Schedule> {
    return this.http.get<Schedule>(`/api/v1/schedules/${date}`);
  }

  computeSchedule(date: string, crewCapacity: number): Observable<Schedule> {
    return this.http.post<Schedule>(`/api/v1/schedules/${date}/compute`, { crewCapacity });
  }

  disruptions(): Observable<Disruption[]> {
    return this.http.get<Disruption[]>('/api/v1/disruptions');
  }

  disruption(id: string): Observable<Disruption> {
    return this.http.get<Disruption>(`/api/v1/disruptions/${id}`);
  }

  reportDisruption(body: Record<string, unknown>): Observable<Disruption> {
    return this.http.post<Disruption>('/api/v1/disruptions', body);
  }

  approve(id: string, strategy: string, note: string | null): Observable<Disruption> {
    return this.http.post<Disruption>(`/api/v1/disruptions/${id}/approve`, { strategy, note });
  }

  reject(id: string, note: string): Observable<Disruption> {
    return this.http.post<Disruption>(`/api/v1/disruptions/${id}/reject`, { note });
  }

  audit(id: string): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`/api/v1/disruptions/${id}/audit`);
  }

  ask(question: string): Observable<AskResponse> {
    return this.http.post<AskResponse>('/api/v1/assistant/ask', { question });
  }
}

/** A short, human message from an error response: the problem detail when there is one. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as Problem | null;
    if (problem && typeof problem === 'object' && problem.detail) {
      return problem.detail;
    }
    if (error.status === 0) {
      return 'The DockMind services are not reachable. Are they running?';
    }
    if (error.status === 401) {
      return 'Your session has expired. Please sign in again.';
    }
    if (error.status === 403) {
      return 'Your role does not allow this.';
    }
    if (error.status === 429) {
      return 'Too many requests. Please wait a moment.';
    }
    return `Request failed (${error.status}).`;
  }
  return 'Something went wrong.';
}
