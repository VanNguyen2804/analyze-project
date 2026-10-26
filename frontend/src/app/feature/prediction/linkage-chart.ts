import {
  CategoryLinkageChartData,
  DrawSequenceNode,
  SequenceTransitionLink,
  NumberGapPressureItem,
  DeltaSpacingDistributionItem,
  SumDistributionItem,
  ParityDistributionItem,
} from '../../core/models/prediction-payload.model';
import { LotteryCategory } from '../../core/models/lottery-category.model';

export interface LinkageHistoryRecord {
  id?: number;
  drawDate: string;
  numbers: number[];
  specialNumber?: number | null;
}

/** Pure chart projection: no HTTP, view state or change detection. */
export function buildLinkageChartData(
  cat: LotteryCategory,
  records: LinkageHistoryRecord[],
): CategoryLinkageChartData | null {
  if (!records || records.length === 0) {
    return null;
  }

  const maxLimit = cat === 'POWER' ? 55 : 45;
  const categoryTitle = cat === 'POWER' ? 'Power 6/55' : 'Mega 6/45';
  const badgeClass = cat === 'POWER' ? 'bg-primary' : 'bg-danger';

  // Sắp xếp theo ngày tăng dần để dựng dòng chảy chuyển dịch từ quá khứ đến hiện tại
  const sortedAsc = [...records].sort((a, b) => {
    const da = new Date(a.drawDate || 0).getTime();
    const db = new Date(b.drawDate || 0).getTime();
    return da - db;
  });

  // Lấy tối đa 7 kỳ gần nhất cho dòng chảy trực quan timeline
  const recentAsc = sortedAsc.slice(-7);

  const recentDraws: DrawSequenceNode[] = recentAsc.map((r: LinkageHistoryRecord) => {
    const rawNums = Array.isArray(r.numbers) ? r.numbers.map(Number) : [];
    const sortedNums = [...rawNums].sort((a, b) => a - b);
    const evenCount = sortedNums.filter((n) => n % 2 === 0).length;
    const oddCount = sortedNums.length - evenCount;
    const sum = sortedNums.reduce((acc, curr) => acc + curr, 0);

    const d = r.drawDate ? new Date(r.drawDate) : null;
    const formatted =
      d && !isNaN(d.getTime())
        ? `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`
        : r.drawDate || 'Kỳ CSDL';

    return {
      drawId: r.id || 0,
      drawDate: r.drawDate || '',
      drawDateFormatted: formatted,
      numbers: sortedNums,
      specialNumber: r.specialNumber ? Number(r.specialNumber) : undefined,
      oddEvenRatio: `${evenCount}C/${oddCount}L`,
      sum,
    };
  });

  // Tính toán liên kết chuyển dịch (Sequence Transition Links) giữa các kỳ kế tiếp nhau
  const transitionLinks: SequenceTransitionLink[] = [];
  let repeatTransitions = 0;
  let driftTransitions = 0;
  let sameTailTransitions = 0;
  const totalTransitions = Math.max(1, sortedAsc.length - 1);

  for (let i = 0; i < sortedAsc.length - 1; i++) {
    const fromDraw = sortedAsc[i];
    const toDraw = sortedAsc[i + 1];
    const fromNums: number[] = (fromDraw.numbers || []).map(Number);
    const toNums: number[] = (toDraw.numbers || []).map(Number);

    let hasRep = false;
    let hasDrift = false;
    let hasTail = false;

    for (const fn of fromNums) {
      for (const tn of toNums) {
        const delta = tn - fn;
        if (delta === 0) {
          hasRep = true;
        } else if (Math.abs(delta) === 1) {
          hasDrift = true;
        } else if (fn % 10 === tn % 10) {
          hasTail = true;
        }
      }
    }

    if (hasRep) repeatTransitions++;
    if (hasDrift) driftTransitions++;
    if (hasTail) sameTailTransitions++;
  }

  // Xây dựng links cụ thể cho timeline gần nhất (recentDraws)
  for (let i = 0; i < recentDraws.length - 1; i++) {
    const fromNode = recentDraws[i];
    const toNode = recentDraws[i + 1];

    fromNode.numbers.forEach((fn, fIdx) => {
      toNode.numbers.forEach((tn, tIdx) => {
        const delta = tn - fn;
        if (delta === 0) {
          transitionLinks.push({
            fromDrawDate: fromNode.drawDate,
            toDrawDate: toNode.drawDate,
            fromNumber: fn,
            toNumber: tn,
            fromIndex: fIdx,
            toIndex: tIdx,
            type: 'REPEAT',
            typeLabel: 'Lặp số chính xác (Δ=0)',
            color: '#10b981',
            delta: 0,
            description: `Bóng [${String(fn).padStart(2, '0')}] bảo toàn nguyên vị trí từ kỳ ${fromNode.drawDateFormatted} sang kỳ ${toNode.drawDateFormatted}`,
          });
        } else if (Math.abs(delta) === 1) {
          transitionLinks.push({
            fromDrawDate: fromNode.drawDate,
            toDrawDate: toNode.drawDate,
            fromNumber: fn,
            toNumber: tn,
            fromIndex: fIdx,
            toIndex: tIdx,
            type: 'DRIFT',
            typeLabel: `Lệch lân cận (${delta > 0 ? '+1' : '-1'})`,
            color: '#f59e0b',
            delta,
            description: `Bóng [${String(fn).padStart(2, '0')}] dịch chuyển ${delta > 0 ? '+1 nút' : '-1 nút'} thành [${String(tn).padStart(2, '0')}]`,
          });
        } else if (fn % 10 === tn % 10) {
          transitionLinks.push({
            fromDrawDate: fromNode.drawDate,
            toDrawDate: toNode.drawDate,
            fromNumber: fn,
            toNumber: tn,
            fromIndex: fIdx,
            toIndex: tIdx,
            type: 'SAME_TAIL',
            typeLabel: `Cùng hàng đơn vị (*${fn % 10})`,
            color: '#8b5cf6',
            delta,
            description: `Bóng [${String(fn).padStart(2, '0')}] cộng hưởng cùng hàng đơn vị (*${fn % 10}) sang [${String(tn).padStart(2, '0')}]`,
          });
        }
      });

      // Di cư bóng đặc biệt Jackpot 2 trong Power 6/55
      if (fromNode.specialNumber) {
        const sp = fromNode.specialNumber;
        toNode.numbers.forEach((tn, tIdx) => {
          if (tn === sp) {
            transitionLinks.push({
              fromDrawDate: fromNode.drawDate,
              toDrawDate: toNode.drawDate,
              fromNumber: sp,
              toNumber: tn,
              fromIndex: -1,
              toIndex: tIdx,
              type: 'SPECIAL_MIGRATION',
              typeLabel: 'Bóng ★ di cư vào giải chính',
              color: '#ec4899',
              delta: 0,
              description: `Bóng Jackpot 2 [★${String(sp).padStart(2, '0')}] di chuyển vào dãy 6 số chính kỳ sau [${String(tn).padStart(2, '0')}]`,
            });
          }
        });
      }
    });
  }

  // ==========================================
  // CHART THAY THẾ 1: PHÂN TÍCH ĐỘ TRỄ & ÁP SUẤT POISSON (POISSON GAP & REVERSION PRESSURE)
  // ==========================================
  const expectedInterval = Number((maxLimit / 6).toFixed(2)); // Mega: 7.5; Power: 9.17
  const freqMap: { [num: number]: number } = {};
  const lastSeenIndexMap: { [num: number]: number } = {};
  const lastSeenDateMap: { [num: number]: string } = {};

  for (let n = 1; n <= maxLimit; n++) {
    freqMap[n] = 0;
    lastSeenIndexMap[n] = -1;
    lastSeenDateMap[n] = 'Chưa về trong CSDL mẫu';
  }

  sortedAsc.forEach((r: any, rIdx: number) => {
    const nums = (r.numbers || []).map(Number);
    nums.forEach((n: number) => {
      if (n >= 1 && n <= maxLimit) {
        freqMap[n] = (freqMap[n] || 0) + 1;
        lastSeenIndexMap[n] = rIdx;
        lastSeenDateMap[n] = r.drawDate || '';
      }
    });
  });

  const gapPressureList: NumberGapPressureItem[] = [];
  const totalDrawsCount = sortedAsc.length;

  for (let n = 1; n <= maxLimit; n++) {
    const lastIdx = lastSeenIndexMap[n];
    const currentGap = lastIdx === -1 ? totalDrawsCount : totalDrawsCount - 1 - lastIdx;
    const pressurePercent = Math.min(100, Math.round((currentGap / expectedInterval) * 100));
    const zScore = Number(
      ((currentGap - expectedInterval) / Math.max(1, Math.sqrt(expectedInterval))).toFixed(2),
    );

    let zone: 'HOT' | 'CYCLE' | 'PRESSURE' | 'OVERDUE' = 'CYCLE';
    let zoneLabel = 'Nhịp tuần hoàn chuẩn';
    let zoneBadgeClass = 'badge bg-info text-dark';

    if (currentGap <= 2) {
      zone = 'HOT';
      zoneLabel = '🟢 Đang có đà (Hot)';
      zoneBadgeClass = 'badge bg-success text-white';
    } else if (currentGap <= Math.round(expectedInterval)) {
      zone = 'CYCLE';
      zoneLabel = '🟡 Vùng nhịp tự nhiên';
      zoneBadgeClass = 'badge bg-primary bg-opacity-75 text-white';
    } else if (currentGap <= Math.round(expectedInterval * 1.5)) {
      zone = 'PRESSURE';
      zoneLabel = '🟠 Áp suất phục hồi cao';
      zoneBadgeClass = 'badge bg-warning text-dark';
    } else {
      zone = 'OVERDUE';
      zoneLabel = '🔴 Lô gan tích lũy tới hạn';
      zoneBadgeClass = 'badge bg-danger text-white';
    }

    gapPressureList.push({
      number: n,
      currentGap,
      lastDrawDate: lastSeenDateMap[n],
      expectedInterval,
      pressurePercent,
      frequency: freqMap[n] || 0,
      zone,
      zoneLabel,
      zoneBadgeClass,
      zScore,
    });
  }

  // Top 6 con số có áp suất điểm rơi phục hồi cao nhất (Overdue / Pressure)
  const topPressureNumbers = [...gapPressureList]
    .filter((item) => item.currentGap >= Math.round(expectedInterval))
    .sort((a, b) => b.currentGap - a.currentGap)
    .slice(0, 6);

  // Top 6 con số đang có đà xuất hiện liên tiếp (Hot momentum)
  const topHotNumbers = [...gapPressureList]
    .filter((item) => item.currentGap <= 2)
    .sort((a, b) => a.currentGap - b.currentGap || b.frequency - a.frequency)
    .slice(0, 6);

  // ==========================================
  // CHART THAY THẾ 2: PHỔ DÃN CÁCH DELTA & ĐƯỜNG CONG TỔNG GAUSS
  // ==========================================
  let delta1Count = 0;
  let delta24Count = 0;
  let delta58Count = 0;
  let delta9PlusCount = 0;
  let totalDeltas = 0;

  // Phân phối tổng điểm
  const sumBuckets = {
    b1: 0,
    b2: 0,
    b3: 0,
    b4: 0,
    b5: 0,
  };

  // Phân phối chẵn lẻ
  let p33Count = 0;
  let p42Count = 0;
  let p24Count = 0;
  let pSkewCount = 0;

  sortedAsc.forEach((r: any) => {
    const nums = (r.numbers || []).map(Number).sort((a: number, b: number) => a - b);
    if (nums.length === 6) {
      // Deltas
      for (let i = 0; i < 5; i++) {
        const d = nums[i + 1] - nums[i];
        totalDeltas++;
        if (d === 1) delta1Count++;
        else if (d >= 2 && d <= 4) delta24Count++;
        else if (d >= 5 && d <= 8) delta58Count++;
        else delta9PlusCount++;
      }

      // Sum
      const s = nums.reduce((acc: number, curr: number) => acc + curr, 0);
      if (cat === 'POWER') {
        if (s < 130) sumBuckets.b1++;
        else if (s <= 155) sumBuckets.b2++;
        else if (s <= 185) sumBuckets.b3++;
        else if (s <= 210) sumBuckets.b4++;
        else sumBuckets.b5++;
      } else {
        if (s < 105) sumBuckets.b1++;
        else if (s <= 125) sumBuckets.b2++;
        else if (s <= 150) sumBuckets.b3++;
        else if (s <= 170) sumBuckets.b4++;
        else sumBuckets.b5++;
      }

      // Parity
      const evens = nums.filter((n: number) => n % 2 === 0).length;
      if (evens === 3) p33Count++;
      else if (evens === 4) p42Count++;
      else if (evens === 2) p24Count++;
      else pSkewCount++;
    }
  });

  const safeTotalDeltas = Math.max(1, totalDeltas);
  const deltaDistributions: DeltaSpacingDistributionItem[] = [
    {
      deltaRange: 'Δ = 1',
      minDelta: 1,
      maxDelta: 1,
      count: delta1Count,
      percent: Math.round((delta1Count / safeTotalDeltas) * 100),
      description: 'Cặp số liền kề / kẹp sát nút (ví dụ: 14-15, 31-32)',
      isOptimal: false,
    },
    {
      deltaRange: 'Δ = 2 - 4',
      minDelta: 2,
      maxDelta: 4,
      count: delta24Count,
      percent: Math.round((delta24Count / safeTotalDeltas) * 100),
      description: 'Dãn cách bước ngắn (2 đến 4 đơn vị)',
      isOptimal: false,
    },
    {
      deltaRange: 'Δ = 5 - 8',
      minDelta: 5,
      maxDelta: 8,
      count: delta58Count,
      percent: Math.round((delta58Count / safeTotalDeltas) * 100),
      description: 'Dãn cách vàng bảo toàn độ phủ giải Jackpot',
      isOptimal: true,
    },
    {
      deltaRange: 'Δ ≥ 9',
      minDelta: 9,
      maxDelta: 50,
      count: delta9PlusCount,
      percent: Math.round((delta9PlusCount / safeTotalDeltas) * 100),
      description: 'Bước nhảy dãn cách xa qua các nhóm hàng chục',
      isOptimal: false,
    },
  ];

  const totalDrawsSafe = Math.max(1, sortedAsc.length);
  const sumDistributions: SumDistributionItem[] =
    cat === 'POWER'
      ? [
          {
            sumRange: '< 130',
            minSum: 0,
            maxSum: 129,
            count: sumBuckets.b1,
            percent: Math.round((sumBuckets.b1 / totalDrawsSafe) * 100),
            isPeak: false,
          },
          {
            sumRange: '130 - 155',
            minSum: 130,
            maxSum: 155,
            count: sumBuckets.b2,
            percent: Math.round((sumBuckets.b2 / totalDrawsSafe) * 100),
            isPeak: false,
          },
          {
            sumRange: '156 - 185 (Đỉnh Vàng)',
            minSum: 156,
            maxSum: 185,
            count: sumBuckets.b3,
            percent: Math.round((sumBuckets.b3 / totalDrawsSafe) * 100),
            isPeak: true,
          },
          {
            sumRange: '186 - 210',
            minSum: 186,
            maxSum: 210,
            count: sumBuckets.b4,
            percent: Math.round((sumBuckets.b4 / totalDrawsSafe) * 100),
            isPeak: false,
          },
          {
            sumRange: '> 210',
            minSum: 211,
            maxSum: 330,
            count: sumBuckets.b5,
            percent: Math.round((sumBuckets.b5 / totalDrawsSafe) * 100),
            isPeak: false,
          },
        ]
      : [
          {
            sumRange: '< 105',
            minSum: 0,
            maxSum: 104,
            count: sumBuckets.b1,
            percent: Math.round((sumBuckets.b1 / totalDrawsSafe) * 100),
            isPeak: false,
          },
          {
            sumRange: '105 - 125',
            minSum: 105,
            maxSum: 125,
            count: sumBuckets.b2,
            percent: Math.round((sumBuckets.b2 / totalDrawsSafe) * 100),
            isPeak: false,
          },
          {
            sumRange: '126 - 150 (Đỉnh Vàng)',
            minSum: 126,
            maxSum: 150,
            count: sumBuckets.b3,
            percent: Math.round((sumBuckets.b3 / totalDrawsSafe) * 100),
            isPeak: true,
          },
          {
            sumRange: '151 - 170',
            minSum: 151,
            maxSum: 170,
            count: sumBuckets.b4,
            percent: Math.round((sumBuckets.b4 / totalDrawsSafe) * 100),
            isPeak: false,
          },
          {
            sumRange: '> 170',
            minSum: 171,
            maxSum: 270,
            count: sumBuckets.b5,
            percent: Math.round((sumBuckets.b5 / totalDrawsSafe) * 100),
            isPeak: false,
          },
        ];

  const parityDistributions: ParityDistributionItem[] = [
    {
      ratio: '3 Chẵn - 3 Lẻ',
      count: p33Count,
      percent: Math.round((p33Count / totalDrawsSafe) * 100),
      isDominant: true,
    },
    {
      ratio: '4 Chẵn - 2 Lẻ',
      count: p42Count,
      percent: Math.round((p42Count / totalDrawsSafe) * 100),
      isDominant: false,
    },
    {
      ratio: '2 Chẵn - 4 Lẻ',
      count: p24Count,
      percent: Math.round((p24Count / totalDrawsSafe) * 100),
      isDominant: false,
    },
    {
      ratio: 'Lệch cao (5-1, 1-5, 6-0)',
      count: pSkewCount,
      percent: Math.round((pSkewCount / totalDrawsSafe) * 100),
      isDominant: false,
    },
  ];

  const maxSumCount = Math.max(0, ...sumDistributions.map((item) => item.count));
  sumDistributions.forEach((item) => {
    item.isPeak = item.count > 0 && item.count === maxSumCount;
    item.sumRange = item.sumRange.replace(/ \(.*\)/, '');
  });
  const maxParityCount = Math.max(0, ...parityDistributions.map((item) => item.count));
  parityDistributions.forEach((item) => {
    item.isDominant = item.count > 0 && item.count === maxParityCount;
  });
  const maxDeltaCount = Math.max(0, ...deltaDistributions.map((item) => item.count));
  deltaDistributions.forEach((item) => {
    item.isOptimal = item.count > 0 && item.count === maxDeltaCount;
  });
  const optimalSumRange =
    sumDistributions
      .filter((item) => item.isPeak)
      .map((item) => item.sumRange)
      .join(', ') || 'N/A';
  const recommendedDeltaSpacing =
    deltaDistributions
      .filter((item) => item.isOptimal)
      .map((item) => item.deltaRange)
      .join(', ') || 'N/A';

  return {
    category: cat,
    categoryTitle,
    badgeClass,
    totalDraws: sortedAsc.length,
    maxLimit,
    recentDraws,
    transitionLinks,
    repeatRate: Math.round((repeatTransitions / totalTransitions) * 100),
    drift1Rate: Math.round((driftTransitions / totalTransitions) * 100),
    sameTailRate: Math.round((sameTailTransitions / totalTransitions) * 100),
    gapPressureList,
    topPressureNumbers,
    topHotNumbers,
    deltaDistributions,
    sumDistributions,
    parityDistributions,
    optimalSumRange,
    recommendedDeltaSpacing,
  };
}
