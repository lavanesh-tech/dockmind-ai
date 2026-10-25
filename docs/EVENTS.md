# Event contracts

Services share **messages, not code or databases**. This file is the contract. Producers may
add fields; consumers must ignore fields they don't know. Any other change is a new `eventVersion`.

## `shipment-events` (producer: shipment-service)

- **Key:** the shipment ID, so every event for one shipment is in order on one partition.
- **Headers:** `correlationId` (the originating request), `eventType`.
- **Delivery:** at least once, through the transactional outbox. **Consumers must deduplicate by `eventId`.**
- **Types:** `ShipmentBooked`, `ShipmentArrived`, `ShipmentCancelled`, all `eventVersion: 1`.

```json
{
  "eventId": "6a1f…",                     // unique; the deduplication key
  "eventType": "ShipmentBooked",
  "eventVersion": 1,
  "tenantId": "acme-logistics",           // from the verified token of the causing request
  "aggregateId": "0c9e…",                 // the shipment ID
  "occurredAt": "2026-11-02T07:58:12.481Z",
  "correlationId": "7f3a…",
  "payload": {                            // a full snapshot, not a diff
    "shipmentId": "0c9e…",
    "reference": "PO-2026-1001",
    "direction": "INBOUND",
    "palletCount": 12,
    "requiredCapabilities": ["REFRIGERATED"],
    "priority": "EXPEDITED",
    "windowStart": "2026-11-02T08:00:00Z",
    "windowEnd": "2026-11-02T10:00:00Z",
    "status": "BOOKED",
    "actualArrivalAt": null
  }
}
```

## Consumer rules (dock-scheduling-service)

| Situation | Behaviour |
|---|---|
| New event | Apply to `shipment_view`, record `eventId` in `processed_event`, one transaction |
| Same `eventId` again | Skip (duplicate) |
| `occurredAt` older than the view's last event | Record as processed, don't apply (stale) |
| Malformed JSON, unknown version, missing fields | No retry → `shipment-events.DLT` |
| Other failures (e.g. database down) | 3 retries with backoff 200 ms → 400 ms → 800 ms, then `shipment-events.DLT` |
