export type LotteryCategory = 'MEGA' | 'POWER';

export function normalizeCategory(value: string): LotteryCategory {
  return value.toUpperCase() === 'POWER' ? 'POWER' : 'MEGA';
}
