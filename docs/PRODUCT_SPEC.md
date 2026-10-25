# DockMind AI: Product Specification

## 1. Problem

Warehouses receive and ship goods through a limited number of loading docks. Trucks arrive
late, docks break, and urgent loads appear at short notice. When that happens, dispatchers
usually re-plan by hand, often on a whiteboard or spreadsheet. The result is trucks waiting
for hours, idle docks next to overloaded ones, and no record of why a decision was made.

DockMind schedules dock appointments automatically and, when something goes wrong, proposes
feasible recovery plans that respect every constraint. A human approves any significant change.

## 2. Users and roles

| Role | Who | Can do |
|---|---|---|
| `ADMIN` | Warehouse operations manager | Manage warehouses, docks, users and settings for their tenant |
| `DISPATCHER` | Shift dispatcher | Book shipments, schedule, run recovery, approve plans |
| `VIEWER` | Carrier liaison, analyst | Read schedules, dashboards and history |

A **tenant** is one logistics company. Tenants never see each other's data.

## 3. Core concepts

| Term | Meaning |
|---|---|
| Warehouse | A site with docks, opening hours and a time zone |
| Dock | A loading bay with capabilities (e.g. refrigerated, hazmat, side-load, max trailer length) |
| Shipment | Goods to load or unload, with pallet count, required capabilities and priority |
| Truck arrival | An expected or actual arrival time for a shipment's truck, inside an arrival window |
| Appointment | A shipment assigned to a dock for a time slot |
| Disruption | A late arrival, dock outage or priority change that invalidates part of the schedule |
| Recovery plan | A proposed set of appointment changes that restores a feasible schedule |

## 4. Functional requirements

1. Register warehouses and docks, including capabilities and availability windows.
2. Book shipments and register truck arrivals.
3. Schedule appointments that satisfy dock compatibility, arrival windows, loading duration,
   dock availability and capacity. The scheduler is deterministic: the same input always
   produces the same schedule.
4. Reject conflicting or over-capacity bookings with a clear explanation.
5. Simulate late arrivals and dock outages.
6. Generate and compare recovery plans by waiting time, number of moved appointments and
   priority violations.
7. Require human approval for changes above a configurable threshold.
8. Show an operational dashboard: today's schedule, dock utilization, waiting time, disruptions.
9. Keep an immutable audit trail of every schedule change: who, what, when and why.
10. An AI assistant explains recommendations and answers questions using read-only tools.
    It can never create or change a schedule, and it can never bypass the deterministic validator.

## 5. Non-functional requirements

- **Security:** OAuth2/OIDC sign-in, JWT validation, role-based access, tenant isolation on every query.
- **Reliability:** idempotent event handling, bounded retries, dead-letter queues, no lost events (outbox).
- **Observability:** correlation IDs, distributed tracing, metrics, health and readiness probes.
- **Performance:** scheduling and API latency targets are set in Phase 4 and Phase 12, from measured baselines.

## 6. Success metrics (all from seeded simulations)

Compared against a first-come-first-served (FCFS) baseline on identical seeded workloads:
average and p95 truck waiting time, dock utilization, scheduling conflicts, recovery success
rate, and scheduling computation time. Results are published as CSV/JSON, with the seed and
parameters, so anyone can reproduce them.

## 7. Out of scope

Real customer data, billing, yard management, driver mobile apps, route optimization,
and Kubernetes. Simulated results are never presented as real-world savings.
