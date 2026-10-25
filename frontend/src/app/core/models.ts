export type Capability = 'REFRIGERATED' | 'HAZMAT' | 'SIDE_LOAD' | 'OVERSIZED';
export type Priority = 'STANDARD' | 'EXPEDITED' | 'CRITICAL';
export const CAPABILITIES: Capability[] = ['REFRIGERATED', 'HAZMAT', 'SIDE_LOAD', 'OVERSIZED'];
export const PRIORITIES: Priority[] = ['STANDARD', 'EXPEDITED', 'CRITICAL'];

export interface Shipment {
  id: string;
  reference: string;
  direction: 'INBOUND' | 'OUTBOUND';
  palletCount: number;
  requiredCapabilities: Capability[];
  priority: Priority;
  windowStart: string;
  windowEnd: string;
  status: 'BOOKED' | 'ARRIVED' | 'CANCELLED';
  actualArrivalAt: string | null;
  late: boolean | null;
  version: number;
}

export interface NewShipment {
  reference: string;
  direction: 'INBOUND' | 'OUTBOUND';
  palletCount: number;
  requiredCapabilities: Capability[];
  priority: Priority;
  windowStart: string;
  windowEnd: string;
}

export interface Dock {
  id: string;
  code: string;
  capabilities: Capability[];
  opensAt: string;
  closesAt: string;
}

export interface Appointment {
  shipmentId: string;
  reference: string;
  dockCode: string;
  start: string;
  end: string;
}

export interface Schedule {
  runId: string;
  date: string;
  algorithm: string;
  crewCapacity: number;
  computedAt: string;
  computedBy: string;
  scheduled: number;
  unscheduled: number;
  avgWaitMinutes: number;
  appointments: Appointment[];
  notScheduled: { shipmentId: string; reference: string; reason: string }[];
}

export interface PlanSummary {
  strategy: string;
  rank: number;
  recommended: boolean;
  moved: number;
  scheduled: number;
  unscheduled: number;
  urgentUnscheduled: number;
  avgWaitMinutes: number;
  requiresAdmin: boolean;
}

export interface Disruption {
  id: string;
  type: 'LATE_ARRIVAL' | 'DOCK_OUTAGE';
  scheduleDate: string;
  shipmentId: string | null;
  newArrival: string | null;
  dockCode: string | null;
  outageStart: string | null;
  outageEnd: string | null;
  decisionTime: string;
  status: 'PENDING_APPROVAL' | 'APPLIED' | 'REJECTED' | 'FAILED';
  reportedBy: string;
  reportedAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  appliedStrategy: string | null;
  plans: PlanSummary[];
}

export interface AuditEntry {
  at: string;
  action: string;
  actor: string;
  detail: string;
}

export interface AskResponse {
  answer: string;
  toolsUsed: string[];
  notice: string;
}

/** RFC 9457 problem details, as every DockMind service returns them. */
export interface Problem {
  title?: string;
  detail?: string;
  status?: number;
  correlationId?: string;
}
