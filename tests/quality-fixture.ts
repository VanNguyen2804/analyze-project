import { drawFingerprint, type QualityDraw } from '../src/draw-quality';
// Synthetic model fixtures are explicitly trusted only by the tests.
export function verifiedFixture<T extends QualityDraw>(row: T): T {
  if (row.category === 'POWER' && row.specialNumber == null) row.specialNumber = Array.from({length:55},(_,i)=>i+1).find(n=>!row.numbers.includes(n));
  row.quality = { status: 'SOURCE_MATCHED', fingerprint: drawFingerprint(row), sourceUrl: 'test:synthetic', audit: [] };
  return row;
}
