/** Today's date in UTC as YYYY-MM-DD (schedules are per UTC day). */
export function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}

/** HH:MM in UTC. */
export function hhmm(iso: string | null | undefined): string {
  return iso ? new Date(iso).toISOString().slice(11, 16) : '';
}

/** Combines a YYYY-MM-DD day and HH:MM time (UTC) into an ISO instant. */
export function at(date: string, time: string): string {
  return `${date}T${time}:00Z`;
}
