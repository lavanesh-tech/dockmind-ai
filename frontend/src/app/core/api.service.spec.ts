import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiService, errorMessage } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  it('calls the gateway paths the services expose', () => {
    api.computeSchedule('2026-11-02', 4).subscribe();
    const compute = http.expectOne('/api/v1/schedules/2026-11-02/compute');
    expect(compute.request.method).toBe('POST');
    expect(compute.request.body).toEqual({ crewCapacity: 4 });

    api.approve('d1', 'MINIMAL_CHANGE', null).subscribe();
    const approve = http.expectOne('/api/v1/disruptions/d1/approve');
    expect(approve.request.body).toEqual({ strategy: 'MINIMAL_CHANGE', note: null });

    api.shipments('2026-11-02T00:00:00Z', '2026-11-02T23:59:00Z').subscribe();
    const list = http.expectOne(r => r.url === '/api/v1/shipments');
    expect(list.request.params.get('from')).toBe('2026-11-02T00:00:00Z');
    http.verify();
  });

  it('shows the problem detail from the server, or a friendly fallback', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 409, error: { detail: 'The schedule changed.' } })))
      .toBe('The schedule changed.');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('not reachable');
    expect(errorMessage(new HttpErrorResponse({ status: 403 }))).toContain('role');
    expect(errorMessage(new HttpErrorResponse({ status: 429 }))).toContain('Too many');
    expect(errorMessage('boom')).toBe('Something went wrong.');
  });
});
