// bofo-announce / limits.ts - rate-limit decision (10 per minute, 40 per day, per user) from the timestamps of the user's announce rows. Pure.
export const PER_MINUTE = 10, PER_DAY = 40;
const MIN = 60_000, DAY = 86_400_000;

/** `stamps` = created_at (ms) of this user's announce rows in the last 24h (at most the newest 40 are needed). */
export function decide(stamps: number[], now: number, enforceDay = true): { ok: true } | { ok: false; retry_after: number } {
  const s = [...stamps].sort((a, b) => b - a);                    // newest first
  let wait = 0;
  const inMin = s.filter((t) => now - t < MIN);
  if (inMin.length >= PER_MINUTE) wait = Math.max(wait, Math.ceil((inMin[PER_MINUTE - 1] + MIN - now) / 1000));
  const inDay = s.filter((t) => now - t < DAY);
  if (enforceDay && inDay.length >= PER_DAY) wait = Math.max(wait, Math.ceil((inDay[PER_DAY - 1] + DAY - now) / 1000));
  return wait > 0 ? { ok: false, retry_after: wait } : { ok: true };
}

/** Postgres 42703 / PostgREST PGRST204: the `kind` column does not exist yet (migration 031 not applied). */
export function isMissingKindColumn(err: { code?: string; message?: string } | null | undefined): boolean {
  if (!err) return false;
  if (err.code === "42703" || err.code === "PGRST204") return true;
  return /kind/i.test(err.message ?? "") && /(column|schema cache)/i.test(err.message ?? "");
}
