export function isNearMiss(predicted: number, official: number[] | null | undefined): boolean {
  return (
    !!official &&
    !official.includes(predicted) &&
    official.some((n) => Math.abs(n - predicted) === 1)
  );
}

export function nearMissTarget(
  predicted: number,
  official: number[] | null | undefined,
): number | null {
  return official?.find((n) => Math.abs(n - predicted) === 1) ?? null;
}

export function formatAnalysisNumber(value: number | null | undefined): string {
  return value == null ? '--' : value < 10 ? '0' + value : String(value);
}

export function formatAnalysisMetric(value: number | null | undefined, suffix = ''): string {
  return value == null ? 'N/A' : `${value}${suffix}`;
}
