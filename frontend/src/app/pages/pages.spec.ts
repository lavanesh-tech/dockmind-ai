import { describe, expect, it } from 'vitest';
import { appointmentsPerDock } from './dashboard.page';
import { byDock } from './schedule.page';
import { mayApprove } from './disruption-detail.page';
import { PlanSummary, Schedule } from '../core/models';

const appt = (dockCode: string, start: string) =>
  ({ shipmentId: dockCode + start, reference: 'R', dockCode, start, end: start });

describe('page logic', () => {
  it('counts appointments per dock in dock order', () => {
    const s = { appointments: [appt('D2', 'a'), appt('D1', 'b'), appt('D2', 'c')] } as unknown as Schedule;
    expect(appointmentsPerDock(s)).toEqual({ labels: ['D1', 'D2'], counts: [1, 2] });
    expect(appointmentsPerDock(null)).toEqual({ labels: [], counts: [] });
  });

  it('groups appointments by dock, sorted by start', () => {
    const groups = byDock([appt('D2', '2026-11-02T09:00:00Z'), appt('D1', '2026-11-02T10:00:00Z'), appt('D2', '2026-11-02T08:00:00Z')]);
    expect(groups.map(g => g.dock)).toEqual(['D1', 'D2']);
    expect(groups[1].items.map(i => i.start)).toEqual(['2026-11-02T08:00:00Z', '2026-11-02T09:00:00Z']);
  });

  it('large plans need an admin; viewers never approve', () => {
    const small = { requiresAdmin: false } as PlanSummary;
    const large = { requiresAdmin: true } as PlanSummary;
    expect(mayApprove(small, true, false)).toBe(true);
    expect(mayApprove(large, true, false)).toBe(false);
    expect(mayApprove(large, true, true)).toBe(true);
    expect(mayApprove(small, false, false)).toBe(false);
  });
});
