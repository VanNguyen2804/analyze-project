import corrections from '../backend/src/main/resources/data/reviewed_draw_corrections.json';

export interface QualityDraw {
  category?: string; drawDate: string; numbers: number[]; specialNumber?: number | null; note?: string | null;
  quality?: { status: string; fingerprint: string; sourceUrl: string; sourceDrawId?: string; audit: any[] };
}
export function drawFingerprint(row: QualityDraw): string {
  return `${row.category}|${row.drawDate}|${[...(row.numbers ?? [])].sort((a,b) => a-b).join(',')}|${row.specialNumber ?? ''}`;
}
export function eligibleDraw(row: QualityDraw): boolean {
  const max = row.category === 'POWER' ? 55 : 45;
  return ['MEGA','POWER'].includes(row.category ?? '') && row.numbers?.length === 6
    && new Set(row.numbers).size === 6 && row.numbers.every(n => Number.isInteger(n) && n >= 1 && n <= max)
    && (row.category !== 'POWER' ? row.specialNumber == null : Number.isInteger(row.specialNumber)
      && row.specialNumber! >= 1 && row.specialNumber! <= 55 && !row.numbers.includes(row.specialNumber!))
    && row.quality?.status === 'SOURCE_MATCHED' && row.quality.fingerprint === drawFingerprint(row);
}
export function eligibleHistory<T extends QualityDraw & { createdAt?: string; id?: number }>(rows: T[]): T[] {
  const seen = new Set<string>();
  return [...rows].sort((a,b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || (b.id ?? 0) - (a.id ?? 0))
    .filter(row => { const key = `${row.category}|${row.drawDate}`; if (seen.has(key)) return false; seen.add(key); return true; })
    .filter(eligibleDraw);
}
function snapshot(row: QualityDraw) {
  return { category: row.category, drawDate: row.drawDate, numbers: row.numbers == null ? null : [...row.numbers], specialNumber: row.specialNumber ?? null, note: row.note ?? null };
}
export function reconcileQuality<T extends QualityDraw>(row: T, incoming: QualityDraw | undefined, sourceUrl: string,
  now: Date, reasonOverride?: string): T {
  let result = { ...row }, reason = reasonOverride ?? 'SOURCE_NOT_FOUND', status = 'QUARANTINED', evidenceUrl: string | undefined;
  if (incoming && !reasonOverride) {
    const exact = drawFingerprint(row) === drawFingerprint(incoming);
    const missingSpecial = row.category === 'POWER' && row.specialNumber == null
      && [...(row.numbers ?? [])].sort((a,b)=>a-b).join(',') === [...incoming.numbers].sort((a,b)=>a-b).join(',');
    const reviewed = corrections.find(c => c.category === row.category && c.drawDate === row.drawDate
      && drawFingerprint({ ...c, numbers: c.beforeNumbers, specialNumber: c.beforeSpecial }) === drawFingerprint(row)
      && drawFingerprint(c) === drawFingerprint(incoming));
    if (exact || missingSpecial || reviewed) {
      status = 'SOURCE_MATCHED'; reason = exact ? 'MATCHED_SOURCE' : missingSpecial ? 'FILLED_MISSING_SPECIAL' : 'REVIEWED_CORRECTION';
      result = { ...row, numbers: [...incoming.numbers], specialNumber: incoming.specialNumber ?? undefined };
      evidenceUrl = reviewed?.evidenceUrl;
      if (!exact) result.note = `Reconciled against ${sourceUrl}; original values and note retained in quality.audit`;
    } else reason = 'SOURCE_CONFLICT';
  }
  const fingerprint = drawFingerprint(result);
  // Preserve audit history without appending a duplicate event on every scheduler poll.
  if (row.quality?.status === status && row.quality.fingerprint === fingerprint && row.quality.sourceUrl === sourceUrl
      && (status === 'SOURCE_MATCHED' || row.quality.audit.at(-1)?.reason === reason)) return row;
  result.quality = { status, fingerprint, sourceUrl, sourceDrawId: (incoming as any)?.sourceDrawId,
    audit: [...(row.quality?.audit ?? []), { at: now.toISOString(), reason, sourceUrl, evidenceUrl,
      before: snapshot(row), after: snapshot(result), previousStatus: row.quality?.status ?? 'UNVERIFIED' }] };
  return result;
}
