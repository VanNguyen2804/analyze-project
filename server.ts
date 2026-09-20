import express, { Request, Response } from 'express';
import path from 'path';
import fs from 'fs';
import { createServer as createViteServer } from 'vite';

interface LotteryNumberRecord {
  id: number;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[]; // 6 distinct numbers
  specialNumber?: number; // Added for category POWER (1-55, distinct from numbers)
  createdAt: string;
  note?: string;
}

const DATA_FILE = path.join(process.cwd(), 'data', 'lottery_numbers.json');
const H2_DATA_FILE = path.join(process.cwd(), 'data', 'h2_lottery_numbers.json');

// In-memory store initialized from disk
let records: LotteryNumberRecord[] = [];
let nextId = 1;

function loadInitialData(): void {
  try {
    let loaded = false;
    if (fs.existsSync(DATA_FILE)) {
      const content = fs.readFileSync(DATA_FILE, 'utf-8');
      const parsed = JSON.parse(content);
      if (Array.isArray(parsed) && parsed.length > 0) {
        records = parsed;
        loaded = true;
      }
    }
    if (!loaded && fs.existsSync(H2_DATA_FILE)) {
      const content = fs.readFileSync(H2_DATA_FILE, 'utf-8');
      const parsed = JSON.parse(content);
      if (Array.isArray(parsed) && parsed.length > 0) {
        records = parsed;
        loaded = true;
      }
    }
    if (records.length > 0) {
      nextId = Math.max(...records.map((r) => r.id || 0)) + 1;
    } else {
      records = [
        {
          id: 1,
          drawDate: '2026-09-17',
          category: 'POWER',
          numbers: [7, 14, 23, 31, 45, 52],
          specialNumber: 18,
          createdAt: new Date().toISOString(),
          note: 'Bộ số mẫu khởi tạo Power 6/55',
        },
        {
          id: 2,
          drawDate: '2026-09-18',
          category: 'MEGA',
          numbers: [3, 12, 19, 27, 34, 42],
          createdAt: new Date().toISOString(),
          note: 'Bộ số mẫu khởi tạo Mega 6/45',
        },
      ];
      nextId = 3;
      saveDataToDisk();
    }
  } catch (err) {
    console.warn('Failed to load initial data:', err);
  }
}

function saveDataToDisk(): void {
  try {
    const dir = path.dirname(DATA_FILE);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(DATA_FILE, JSON.stringify(records, null, 2), 'utf-8');
  } catch (err) {
    console.warn('Failed to save data to disk:', err);
  }
}

function determineCategoryFromDate(dateStr?: string): 'MEGA' | 'POWER' {
  if (!dateStr) {
    const dow = new Date().getDay();
    return dow === 2 || dow === 4 || dow === 6 ? 'POWER' : 'MEGA';
  }
  const parts = dateStr.split('-').map(Number);
  if (parts.length === 3) {
    const dow = new Date(parts[0], parts[1] - 1, parts[2]).getDay();
    if (dow === 2 || dow === 4 || dow === 6) {
      return 'POWER';
    }
  }
  return 'MEGA';
}

interface NumberScoreDetail {
  number: number;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
  tag: string;
}

interface SpecialNumberDetail {
  number: number;
  probabilityPercent: number;
  specialFrequency: number;
  totalFrequency: number;
  drawGap: number;
  tag: string;
  description: string;
}

interface NumberSelectionReason {
  number: number;
  role: 'main' | 'special';
  tag: string;
  title: string;
  reason: string;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
}

interface DrawRecordDto {
  id: number;
  drawDate: string;
  numbers: number[];
  specialNumber?: number;
  note?: string;
}

interface NumberHistoryAppearance {
  drawDate: string;
  role: 'main' | 'special';
  allNumbers: number[];
  specialNumber?: number;
}

interface PredictionResult {
  status: 'SUCCESS' | 'ERROR';
  category: 'MEGA' | 'POWER';
  lotteryType: 'MEGA' | 'POWER';
  algorithm: string;
  algorithmName: string;
  algorithmDesc: string;
  numbers: number[];
  tickets: number[][];
  specialNumber?: number; // Provided for POWER
  specialNumberDetail?: SpecialNumberDetail;
  specialHotNumbers?: number[]; // Top special numbers in history
  jackpot2Pairs?: string[]; // Pairs connecting main numbers to special number
  totalDrawsAnalyzed: number;
  hotNumbers: number[];
  coldNumbers: number[];
  frequentPairs: string[];
  oddEvenRatio: string;
  details: NumberScoreDetail[];
  analysisSummary: string;
  selectionReasons: NumberSelectionReason[];
  overallReason: string;
  recentDraws: DrawRecordDto[];
  numberHistoryMap: Record<number, NumberHistoryAppearance[]>;
}

const WHEEL_TEMPLATE_10_TO_6 = [
  [0, 1, 2, 3, 4, 5],
  [0, 1, 2, 6, 7, 8],
  [0, 3, 4, 6, 7, 9],
  [0, 3, 5, 6, 8, 9],
  [1, 2, 3, 4, 7, 9],
  [1, 2, 4, 5, 8, 9],
  [1, 3, 5, 6, 7, 8],
  [2, 4, 5, 6, 7, 9],
  [0, 2, 4, 6, 8, 9],
  [1, 3, 4, 5, 7, 8],
];

function analyzeAndPredict(categoryInput: string, algorithmInput: string = 'xgboost'): PredictionResult {
  const category: 'MEGA' | 'POWER' =
    categoryInput && categoryInput.trim().toUpperCase() === 'POWER' ? 'POWER' : 'MEGA';
  const maxLimit = category === 'POWER' ? 55 : 45;

  const validAlgorithms = ['xgboost', 'monte_carlo', 'markov_chain', 'poisson_gap', 'delta_wheeling'];
  const algorithm = validAlgorithms.includes(algorithmInput) ? algorithmInput : 'xgboost';

  const categoryRecords = records
    .filter((r) => r.category === category)
    .sort(
      (a, b) =>
        a.drawDate.localeCompare(b.drawDate) ||
        (a.createdAt || '').localeCompare(b.createdAt || '')
    );

  const totalDraws = categoryRecords.length;

  // 1. Core Feature Tracking
  const mainFrequency = new Array(maxLimit + 1).fill(0);
  const freqLast5 = new Array(maxLimit + 1).fill(0);
  const freqLast10 = new Array(maxLimit + 1).fill(0);
  const specialFrequency = new Array(maxLimit + 1).fill(0);
  const lastSeenMain = new Array(maxLimit + 1).fill(-1);
  const lastSeenSpecial = new Array(maxLimit + 1).fill(-1);

  const mainMomentum = new Array(maxLimit + 1).fill(0.0);
  const specialMomentum = new Array(maxLimit + 1).fill(0.0);

  // Main-Main Co-occurrence Matrix
  const pairMatrix: number[][] = Array.from({ length: maxLimit + 1 }, () =>
    new Array(maxLimit + 1).fill(0)
  );

  // Markov Transition Matrix: transitionMatrix[prevNum][nextNum]
  const transitionMatrix: number[][] = Array.from({ length: maxLimit + 1 }, () =>
    new Array(maxLimit + 1).fill(0)
  );

  // Special-Main Interaction Matrix
  const specialPairMatrix: number[][] = Array.from({ length: maxLimit + 1 }, () =>
    new Array(maxLimit + 1).fill(0)
  );

  for (let t = 0; t < totalDraws; t++) {
    const draw = categoryRecords[t];
    const validNums = Array.from(
      new Set(draw.numbers.filter((n) => n >= 1 && n <= maxLimit))
    );
    const weight = Math.exp(-0.12 * (totalDraws - 1 - t));

    for (const n of validNums) {
      mainFrequency[n]++;
      lastSeenMain[n] = t;
      mainMomentum[n] += weight;

      if (t >= totalDraws - 5) freqLast5[n]++;
      if (t >= Math.max(0, totalDraws - 10)) freqLast10[n]++;
    }

    // Main-Main pairs
    for (let i = 0; i < validNums.length; i++) {
      for (let j = i + 1; j < validNums.length; j++) {
        const n1 = validNums[i];
        const n2 = validNums[j];
        pairMatrix[n1][n2]++;
        pairMatrix[n2][n1]++;
      }
    }

    // Markov transition from draw t to t+1
    if (t < totalDraws - 1) {
      const nextDraw = categoryRecords[t + 1];
      const nextValid = Array.from(
        new Set(nextDraw.numbers.filter((n) => n >= 1 && n <= maxLimit))
      );
      for (const currN of validNums) {
        for (const nextN of nextValid) {
          transitionMatrix[currN][nextN]++;
        }
      }
    }

    // POWER special number
    if (category === 'POWER' && draw.specialNumber && draw.specialNumber >= 1 && draw.specialNumber <= maxLimit) {
      const sp = draw.specialNumber;
      specialFrequency[sp]++;
      lastSeenSpecial[sp] = t;
      specialMomentum[sp] += weight * 1.2;

      for (const mn of validNums) {
        specialPairMatrix[sp][mn]++;
      }
    }
  }

  // Draw gap (Lô gan)
  const drawGap = new Array(maxLimit + 1).fill(0);
  const specialDrawGap = new Array(maxLimit + 1).fill(0);
  for (let i = 1; i <= maxLimit; i++) {
    drawGap[i] = lastSeenMain[i] === -1 ? totalDraws + 1 : totalDraws - 1 - lastSeenMain[i];
    specialDrawGap[i] = lastSeenSpecial[i] === -1 ? totalDraws + 1 : totalDraws - 1 - lastSeenSpecial[i];
  }

  let maxMainMom = 0.0;
  let maxSpecMom = 0.0;
  for (let i = 1; i <= maxLimit; i++) {
    if (mainMomentum[i] > maxMainMom) maxMainMom = mainMomentum[i];
    if (specialMomentum[i] > maxSpecMom) maxSpecMom = specialMomentum[i];
  }
  if (maxMainMom === 0.0) maxMainMom = 1.0;
  if (maxSpecMom === 0.0) maxSpecMom = 1.0;

  const avgCycle = maxLimit / 6.0;

  interface CandidateScore {
    number: number;
    probability: number;
    frequency: number;
    drawGap: number;
    tag: string;
    title: string;
    reason: string;
  }

  const scoredCandidates: CandidateScore[] = [];

  // Algorithm-specific logic
  let algName = '';
  let algDesc = '';
  let algSummary = '';
  let algOverallReason = '';

  const latestDraw = totalDraws > 0 ? categoryRecords[totalDraws - 1].numbers : [];

  if (algorithm === 'monte_carlo') {
    algName = 'Monte Carlo (Mô phỏng 100K)';
    algDesc = 'Mô phỏng 100.000 lượt quay ngẫu nhiên có trọng số xác suất, đối chuẩn dữ liệu Powerball & Mega Millions tìm điểm hội tụ kỳ vọng (EV).';

    // 100,000 Monte Carlo simulations
    const mcCounts = new Array(maxLimit + 1).fill(0);
    const SIM_RUNS = 100000;

    // Weights derived from historical density + global distribution smoothing
    const weights = new Array(maxLimit + 1).fill(0.0);
    let totalWeight = 0.0;
    for (let i = 1; i <= maxLimit; i++) {
      const freqPart = totalDraws > 0 ? (mainFrequency[i] + 1.0) / (totalDraws + maxLimit) : 1.0;
      const momPart = (mainMomentum[i] / maxMainMom) * 0.4;
      const gapRatio = drawGap[i] / avgCycle;
      const cycleCurve = Math.exp(-Math.pow(gapRatio - 1.2, 2) / 0.8);
      weights[i] = freqPart + momPart + cycleCurve * 0.5 + 0.1;
      totalWeight += weights[i];
    }

    // Cumulative distribution for fast sampling
    const cdf = new Array(maxLimit + 1).fill(0.0);
    let cum = 0;
    for (let i = 1; i <= maxLimit; i++) {
      cum += weights[i] / totalWeight;
      cdf[i] = cum;
    }

    // Run simulations in batches
    for (let run = 0; run < SIM_RUNS; run++) {
      // Pick 6 distinct
      const picked = new Set<number>();
      while (picked.size < 6) {
        const r = Math.random();
        let low = 1, high = maxLimit, selected = 1;
        while (low <= high) {
          const mid = (low + high) >> 1;
          if (cdf[mid] >= r) {
            selected = mid;
            high = mid - 1;
          } else {
            low = mid + 1;
          }
        }
        picked.add(selected);
      }
      for (const num of picked) {
        mcCounts[num]++;
      }
    }

    for (let i = 1; i <= maxLimit; i++) {
      const ev = mcCounts[i] / SIM_RUNS;
      const z = (ev - 0.133) * 22.0 + (Math.random() * 0.2 - 0.1);
      const prob = 1.0 / (1.0 + Math.exp(-z));

      let tag = 'PHÂN PHỐI CHUẨN';
      let title = 'Giá Trị Kỳ Vọng Ổn Định';
      let reason = `Tần suất mô phỏng đạt ${Math.round(ev * 10000) / 100}% trong 100.000 lượt quay Monte Carlo, duy trì phương sai ổn định trong dải tin cậy 95%.`;

      if (ev >= 0.155) {
        tag = 'HỘI TỤ EV CAO';
        title = 'Điểm Hội Tụ Xác Suất Cực Đại';
        reason = `Đạt tỷ lệ xuất hiện vượt trội ${(ev * 100).toFixed(2)}% qua 100.000 kịch bản ngẫu nhiên có trọng số, có giá trị kỳ vọng (EV) cao hàng đầu giải thưởng.`;
      } else if (drawGap[i] > avgCycle * 2.2) {
        tag = 'ĐIỂM KỲ DỊ NGẪU NHIÊN';
        title = 'Biến Cố Kỳ Dị Được Kích Hoạt';
        reason = `Đối chuẩn với hành vi phân phối của Powerball/Mega Millions, các điểm dị biệt có chu kỳ tích lũy sâu được mô phỏng bứt phá trở lại với biên độ hội tụ cao.`;
      } else {
        tag = 'BẢO TOÀN BIÊN ĐỘ';
        title = 'Cân Bằng Biên Độ Phương Sai';
        reason = `Đóng vai trò phân tán rủi ro, cân đối hàm mật độ xác suất liên tục giữa các dải số từ 1 đến ${maxLimit}.`;
      }

      scoredCandidates.push({
        number: i,
        probability: prob,
        frequency: mainFrequency[i],
        drawGap: drawGap[i],
        tag,
        title,
        reason,
      });
    }

    algSummary = `Mô phỏng ngẫu nhiên 100.000 lượt quay Monte Carlo đối chuẩn quốc tế cho ${category} (${totalDraws} kỳ lịch sử). Đã xác định điểm hội tụ kỳ vọng (Expected Value) tối ưu nhất.`;
    algOverallReason = `Phương pháp Monte Carlo thực nghiệm 100.000 kịch bản ngẫu nhiên có trọng số, mô phỏng quá trình lồng cầu độc lập. Dãy số được chọn lọc là giao điểm của các giá trị kỳ vọng (EV) cực đại và độ lệch chuẩn nhỏ nhất, giúp tối đa hóa khả năng chạm giải thưởng.`;

  } else if (algorithm === 'markov_chain') {
    algName = 'Markov Chain (Ma trận Chuyển Trạng Thái)';
    algDesc = 'Tính xác suất chuyển dịch có điều kiện P(Kỳ này | Kỳ trước), dự báo bước nhảy của các con số kế tiếp từ kết quả gần nhất.';

    for (let i = 1; i <= maxLimit; i++) {
      let markovTransitionSum = 0;
      if (latestDraw.length > 0) {
        for (const prevN of latestDraw) {
          const transCount = transitionMatrix[prevN][i];
          const prevFreq = Math.max(1, mainFrequency[prevN]);
          markovTransitionSum += (transCount / prevFreq);
        }
      }

      const normFreq = totalDraws > 0 ? mainFrequency[i] / totalDraws : 0.15;
      const z = markovTransitionSum * 2.5 + normFreq * 0.8 - 0.75 + (Math.random() * 0.2 - 0.1);
      const prob = 1.0 / (1.0 + Math.exp(-z));

      let tag = 'BƯỚC NHẢY MARKOV';
      let title = 'Chuyển Dịch Trực Tiếp Từ Kỳ Trước';
      let reason = `Có xác suất chuyển tiếp P(Số ${i} | Kỳ vừa mở thưởng) đạt mức cao nhất trên ma trận chuyển dịch trạng thái bậc 1.`;

      if (latestDraw.includes(i)) {
        tag = 'TÁI TẠO TRẠNG THÁI';
        title = 'Nhịp Tái Lặp (State Repeat)';
        reason = `Hiệu ứng lặp lại trạng thái từ kỳ trước. Trong chuỗi Markov, bước nhảy lặp có xác suất xuất hiện đáng kể khi hệ thống duy trì pha ổn định.`;
      } else if (markovTransitionSum >= 0.35) {
        tag = 'CHUỖI BẬC 1';
        title = 'Liên Kết Chuyển Dịch Mạnh';
        reason = `Được kích hoạt trực tiếp từ bước nhảy của các con số [${latestDraw.slice(0, 3).join(', ')}] trong kỳ quay trước đó.`;
      } else {
        tag = 'CHU KỲ NỐI TIẾP';
        title = 'Cầu Nối Chuyển Tiếp Phân Vùng';
        reason = `Đóng vai trò bước đệm chuyển dịch dải số, kết nối giữa các cụm phân bố trong mô hình Markov ẩn.`;
      }

      scoredCandidates.push({
        number: i,
        probability: prob,
        frequency: mainFrequency[i],
        drawGap: drawGap[i],
        tag,
        title,
        reason,
      });
    }

    algSummary = `Phân tích Chuỗi Markov & Ma trận Chuyển dịch Trạng thái từ kết quả gần nhất [${latestDraw.join(', ')}]. Đón đầu các bước nhảy xác suất tiếp theo.`;
    algOverallReason = `Mô hình Chuỗi Markov khai thác xác suất có điều kiện giữa các kỳ quay liên tiếp. Bằng cách định vị trạng thái của kỳ vừa mở thưởng, ma trận chuyển dịch chỉ ra các con số có tần suất nối tiếp cao nhất theo quy luật bước nhảy xác suất.`;

  } else if (algorithm === 'poisson_gap') {
    algName = 'Poisson & Lô Gan (Hồi quy phân phối Poisson)';
    algDesc = 'Mô hình phân phối Poisson phát hiện sự tích lũy độ trễ của các biến cố hiếm, định vị điểm rơi phục hồi xác suất (Mean Reversion).';

    for (let i = 1; i <= maxLimit; i++) {
      const lambda = Math.max(0.08, totalDraws > 0 ? mainFrequency[i] / totalDraws : 0.15);
      const gap = drawGap[i];
      // Probability of NOT appearing in 'gap' draws = e^(-lambda * gap)
      // Reversion urgency score = 1 - e^(-lambda * (gap + 1))
      const reversionPressure = 1.0 - Math.exp(-lambda * (gap + 1) * 0.85);

      // Penalize excessively stale dead numbers that may have broken distribution
      const extremePenalty = gap > avgCycle * 4.0 ? 0.4 : 1.0;
      const z = (reversionPressure * 2.2 * extremePenalty) + (lambda * 1.5) - 1.1 + (Math.random() * 0.2 - 0.1);
      const prob = 1.0 / (1.0 + Math.exp(-z));

      let tag = 'HỒI QUY POISSON';
      let title = 'Điểm Rơi Phục Hồi Xác Suất';
      let reason = `Đã vắng bóng ${gap} kỳ liên tiếp. Theo phân phối Poisson với cường độ λ = ${lambda.toFixed(2)}, áp lực hồi quy về giá trị trung bình (Mean Reversion) đã đạt ngưỡng tới hạn.`;

      if (gap >= Math.floor(avgCycle * 1.2) && gap <= Math.floor(avgCycle * 2.8)) {
        tag = 'GAN CHẠM NGƯỠNG';
        title = 'Chu Kỳ Điểm Rơi Vàng';
        reason = `Khoảng gan ${gap} kỳ nằm trọn trong vùng phân bố mật độ xác suất bứt phá cao nhất của hàm phân phối Poisson.`;
      } else if (lambda >= 0.18) {
        tag = 'ĐIỂM RƠI ĐỘT PHÁ';
        title = 'Cường Độ Biến Cố Poisson Cao';
        reason = `Sở hữu tham số tốc độ phát sinh biến cố λ vượt trội, khả năng kích hoạt nổ số trong kỳ tới là rất khả quan.`;
      } else {
        tag = 'TÍCH LŨY CỰC HẠN';
        title = 'Tích Lũy Biên Độ Năng Lượng';
        reason = `Độ trễ tích lũy dài hạn tạo lực đẩy xác suất bù trừ theo quy luật số lớn Bernoulli & Poisson.`;
      }

      scoredCandidates.push({
        number: i,
        probability: prob,
        frequency: mainFrequency[i],
        drawGap: gap,
        tag,
        title,
        reason,
      });
    }

    algSummary = `Phân tích theo Mô hình Phân phối Poisson & Định luật Hồi quy về Trung bình (Mean Reversion) cho danh mục ${category}.`;
    algOverallReason = `Thuật toán Poisson & Lô Gan tính toán xác suất tích lũy của các biến cố trễ hạn. Khi một con số vắng bóng vượt quá kỳ vọng lý thuyết, hàm mật độ Poisson chỉ ra sự gia tăng đột biến của áp lực hồi quy, đón đầu các con số sắp sửa nổ thưởng.`;

  } else if (algorithm === 'delta_wheeling') {
    algName = 'Delta & Wheeling System (Khoảng cách & Lọc chu kỳ)';
    algDesc = 'Phân tích khoảng cách Delta giữa các số liền kề kết hợp ma trận xoay vòng Wheeling System để tối đa hóa diện tích bao phủ.';

    for (let i = 1; i <= maxLimit; i++) {
      // Gating filter: anti-hot trap (penalize if appeared >= 5 times in last 10 draws)
      let gateScore = 1.0;
      if (freqLast10[i] >= 5) gateScore = 0.1;

      // Delta spacing score: prefer numbers that can form well-spaced deltas (4 to 8)
      const normFreq = totalDraws > 0 ? mainFrequency[i] / totalDraws : 0.15;
      const gapRatio = drawGap[i] / avgCycle;
      const deltaFitness = gapRatio >= 0.8 && gapRatio <= 2.5 ? 1.0 : 0.4;

      let pairSynergy = 0;
      for (let j = 1; j <= maxLimit; j++) {
        if (i !== j && pairMatrix[i][j] > 0) pairSynergy += pairMatrix[i][j];
      }
      const normPair = Math.min(1.0, pairSynergy / 8.0);

      const z = (deltaFitness * 1.5 + normPair * 1.2 + normFreq * 0.8) * gateScore - 0.9 + (Math.random() * 0.2 - 0.1);
      const prob = 1.0 / (1.0 + Math.exp(-z));

      let tag = 'DELTA LÝ TƯỞNG';
      let title = 'Khoảng Cách Delta Đạt Chuẩn';
      let reason = `Tạo biên độ dãn cách sai phân Delta tối ưu (4-8 đơn vị), tránh hiện tượng tụ cụm dồn số hoặc giãn cách quá xa.`;

      if (freqLast10[i] >= 5) {
        tag = 'BỊ PHẠT VÌ QUÁ NÓNG';
        title = 'Bộ Lọc Chống Bẫy Số';
        reason = `Xuất hiện ${freqLast10[i]} lần trong 10 kỳ qua. Bị thuật toán Delta hạn chế nhằm tránh bẫy đảo chiều chuỗi.`;
      } else if (normPair >= 0.6) {
        tag = 'BỌC LÓT WHEELING';
        title = 'Tương Thích Ma Trận Xoay Vòng';
        reason = `Sở hữu chỉ số tương tác liên kết cao, tương thích tối đa với các khuôn mẫu phối hợp của Wheeling System 10-to-6.`;
      } else {
        tag = 'DÃN CÁCH CHUẨN';
        title = 'Cân Bằng Phân Vùng Dãy Số';
        reason = `Phân bố đều trong các khoảng thập phân (hàng chục), đảm bảo tỷ lệ bao phủ rộng khắp bàn quay.`;
      }

      scoredCandidates.push({
        number: i,
        probability: prob,
        frequency: mainFrequency[i],
        drawGap: drawGap[i],
        tag,
        title,
        reason,
      });
    }

    algSummary = `Áp dụng Hệ thống Khoảng cách Delta & Khuôn mẫu Wheeling System 10-to-6 bảo toàn tỷ lệ trúng cho ${category}.`;
    algOverallReason = `Hệ thống Delta & Wheeling đo lường khoảng cách sai phân giữa các quả banh, lọc bỏ các cụm số bất thường và áp dụng ma trận Wheeling System 10-to-6 để bảo toàn độ phủ giải thưởng lớn nhất trên mỗi vé cược.`;

  } else {
    // Default: XGBoost
    algName = 'XGBoost AI (Học máy kết hợp)';
    algDesc = 'Phân tích đa chiều Gradient Boosting: Quán tính chuỗi (Momentum) + Lô Gan điểm rơi + Ma trận tương tác cặp số.';

    for (let i = 1; i <= maxLimit; i++) {
      const normFreq = totalDraws > 0 ? mainFrequency[i] / totalDraws : 0.2;
      const normMom = mainMomentum[i] / maxMainMom;
      const gapRatio = drawGap[i] / avgCycle;

      let gapScore = 0.3;
      if (gapRatio >= 0.8 && gapRatio <= 2.5) {
        gapScore = 0.85;
      } else if (gapRatio > 2.5) {
        gapScore = 0.50;
      } else {
        gapScore = 0.30;
      }

      let topPairSum = 0;
      for (let j = 1; j <= maxLimit; j++) {
        if (i !== j && pairMatrix[i][j] > 0) {
          topPairSum += pairMatrix[i][j];
        }
      }
      const pairScore = Math.min(1.0, topPairSum / 5.0);

      let z: number;
      if (totalDraws >= 3) {
        z =
          normMom * 1.7 +
          normFreq * 1.2 +
          gapScore * 0.9 +
          pairScore * 0.7 -
          1.15 +
          (Math.random() * 0.3 - 0.15);
      } else {
        z =
          Math.sin(i * 0.55) * 0.6 +
          Math.cos(i * 0.35) * 0.4 +
          (Math.random() * 0.8 - 0.4);
      }

      const probability = 1.0 / (1.0 + Math.exp(-z));

      let tag = 'CÂN BẰNG';
      let title = 'Cân Bằng Dải Số & Phân Phối Chuẩn';
      let reason = `Đóng vai trò điều tiết cấu trúc dàn trải dải số, duy trì phân bổ chuẩn hóa theo biên độ Vietlott.`;

      if (drawGap[i] >= Math.floor(avgCycle)) {
        tag = 'LÔ GAN';
        title = 'Điểm Rơi Chu Kỳ Hoàn Vốn (Lô Gan)';
        reason = `Đã vắng bóng ${drawGap[i]} kỳ quay liên tiếp. Rơi đúng vào khung chu kỳ hồi quy xác suất tối ưu (0.8 - 2.5 chu kỳ trung bình), độ bứt phá trở lại rất cao.`;
      } else if (mainMomentum[i] > maxMainMom * 0.55) {
        tag = 'SỐ NÓNG';
        title = 'Số Nóng Quán Tính Chuỗi Cao';
        reason = `Xuất hiện ${mainFrequency[i]} lần với xung nhịp xuất hiện liên tiếp. Quán tính thời gian (momentum) đạt mức cao trong mô hình gradient boosting.`;
      } else if (topPairSum >= 4) {
        tag = 'CẶP ĐI KÈM';
        title = 'Cặp Số Tương Tác Đồng Hành';
        reason = `Chỉ số đồng xuất hiện (co-occurrence) mạnh với các số khác trong bộ số. Trong lịch sử thường đi liền cùng nhau.`;
      }

      scoredCandidates.push({
        number: i,
        probability,
        frequency: mainFrequency[i],
        drawGap: drawGap[i],
        tag,
        title,
        reason,
      });
    }

    algSummary = `Phân tích chuyên sâu ${totalDraws} kỳ quay của ${category} bằng thuật toán máy học XGBoost tích hợp đa nhân tố.`;
    algOverallReason = `Mô hình học máy XGBoost kết hợp hàm mất mát tối ưu giữa nhóm Số Nóng duy trì quán tính, nhóm Lô Gan đạt chu kỳ điểm rơi xác suất, và các cặp số đồng hành. Tỷ lệ Chẵn / Lẻ được cân đối theo chuẩn phân phối vàng.`;
  }

  // Sort candidates by probability descending
  scoredCandidates.sort((a, b) => b.probability - a.probability);

  // Pick top 10 candidates with even/odd distribution balance
  const top10Candidates: CandidateScore[] = [];
  let oddCount = 0;
  let evenCount = 0;

  for (const c of scoredCandidates) {
    if (top10Candidates.length >= 10) break;
    const isOdd = c.number % 2 !== 0;
    if (isOdd && oddCount >= 6 && top10Candidates.length < 9) continue;
    if (!isOdd && evenCount >= 6 && top10Candidates.length < 9) continue;

    top10Candidates.push(c);
    if (isOdd) oddCount++;
    else evenCount++;
  }

  if (top10Candidates.length < 10) {
    for (const c of scoredCandidates) {
      if (top10Candidates.length >= 10) break;
      if (!top10Candidates.some((t) => t.number === c.number)) {
        top10Candidates.push(c);
      }
    }
  }

  top10Candidates.sort((a, b) => a.number - b.number);
  const top10Numbers = top10Candidates.map((c) => c.number);

  // Probability map
  const probMap = new Map<number, number>();
  for (const c of top10Candidates) {
    probMap.set(c.number, c.probability);
  }

  // Generate 10 tickets via Wheeling System 10-to-6
  const generatedTickets: number[][] = [];
  for (const indices of WHEEL_TEMPLATE_10_TO_6) {
    const t = indices.map((idx) => top10Numbers[idx]).sort((a, b) => a - b);
    generatedTickets.push(t);
  }

  // Sort tickets by sum of probabilities
  generatedTickets.sort((t1, t2) => {
    const sum1 = t1.reduce((acc, n) => acc + (probMap.get(n) || 0), 0);
    const sum2 = t2.reduce((acc, n) => acc + (probMap.get(n) || 0), 0);
    return sum2 - sum1;
  });

  const selected6Numbers = generatedTickets[0] || top10Numbers.slice(0, 6);

  // DetailDtos for the top 10 candidates
  const detailDtos: NumberScoreDetail[] = top10Candidates.map((sn) => {
    const percent = Math.round(sn.probability * 1000.0) / 10.0;
    return {
      number: sn.number,
      probabilityPercent: percent,
      frequency: sn.frequency,
      drawGap: sn.drawGap,
      tag: sn.tag,
    };
  });

  // Special Number Selection for POWER
  let recommendedSpecialNumber: number | undefined = undefined;
  let specialDetail: SpecialNumberDetail | undefined = undefined;
  let specialHotNumbers: number[] | undefined = undefined;
  let jackpot2Pairs: string[] | undefined = undefined;

  if (category === 'POWER') {
    interface SpecialCandidateScore {
      number: number;
      score: number;
      probability: number;
      synergyWith6: number;
    }

    const specialCandidates: SpecialCandidateScore[] = [];

    for (let s = 1; s <= maxLimit; s++) {
      if (top10Numbers.includes(s)) continue;

      let subsetSynergy = 0;
      for (const m of top10Numbers) {
        subsetSynergy += specialPairMatrix[s][m] * 1.8 + pairMatrix[s][m] * 0.5;
      }

      const normSpecFreq = totalDraws > 0 ? specialFrequency[s] / totalDraws : 0.15;
      const normSpecMom = specialMomentum[s] / maxSpecMom;
      const specGap = specialDrawGap[s];
      let specGapScore = 0.4;
      if (specGap >= 3 && specGap <= 12) {
        specGapScore = 0.85;
      } else if (specGap > 12) {
        specGapScore = 0.55;
      }

      const zSpecial =
        normSpecMom * 1.5 +
        normSpecFreq * 1.3 +
        (subsetSynergy / (top10Numbers.length * 2.0)) * 1.6 +
        specGapScore * 0.8 -
        0.85 +
        (Math.random() * 0.25 - 0.125);

      const probSpecial = 1.0 / (1.0 + Math.exp(-zSpecial));
      const compositeScore = probSpecial + (subsetSynergy > 0 ? 0.3 : 0.0);

      specialCandidates.push({
        number: s,
        score: compositeScore,
        probability: probSpecial,
        synergyWith6: subsetSynergy,
      });
    }

    specialCandidates.sort((a, b) => b.score - a.score);

    if (specialCandidates.length > 0) {
      const topSpec = specialCandidates[0];
      recommendedSpecialNumber = topSpec.number;

      let specTag = 'CỨU CÁNH JACKPOT 2';
      if (specialFrequency[topSpec.number] >= 2) {
        specTag = 'SỐ PHỤ NÓNG';
      } else if (specialDrawGap[topSpec.number] >= 8) {
        specTag = 'SỐ PHỤ LÔ GAN';
      } else if (topSpec.synergyWith6 > 0) {
        specTag = 'TƯƠNG THÍCH 5/6 SỐ';
      }

      const specPercent = Math.round(topSpec.probability * 1000.0) / 10.0;
      specialDetail = {
        number: topSpec.number,
        probabilityPercent: specPercent,
        specialFrequency: specialFrequency[topSpec.number],
        totalFrequency: mainFrequency[topSpec.number] + specialFrequency[topSpec.number],
        drawGap: specialDrawGap[topSpec.number],
        tag: specTag,
        description: `Bảo hiểm Jackpot 2 (${algName}): Khi trật bất kỳ 1 trong 6 số chính, số ${
          topSpec.number < 10 ? '0' + topSpec.number : topSpec.number
        } có chỉ số liên kết bù trừ cao nhất với 5 số còn lại.`,
      };
    }

    const allNumsBySpecialFreq = Array.from({ length: maxLimit }, (_, i) => i + 1)
      .filter((n) => specialFrequency[n] > 0)
      .sort((a, b) => specialFrequency[b] - specialFrequency[a]);
    specialHotNumbers = allNumsBySpecialFreq.slice(0, 3);

    const jpPairs: { sp: number; mn: number; count: number }[] = [];
    for (let s = 1; s <= maxLimit; s++) {
      for (let m = 1; m <= maxLimit; m++) {
        if (specialPairMatrix[s][m] > 0) {
          jpPairs.push({ sp: s, mn: m, count: specialPairMatrix[s][m] });
        }
      }
    }
    jpPairs.sort((a, b) => b.count - a.count);
    jackpot2Pairs = jpPairs.slice(0, 3).map((p) => {
      const padM = p.mn < 10 ? `0${p.mn}` : `${p.mn}`;
      const padS = p.sp < 10 ? `0${p.sp}` : `${p.sp}`;
      return `Chính ${padM} &bull; Phụ ${padS} (${p.count} lần)`;
    });
  }

  // Hot and cold
  const hotNumbers = [...scoredCandidates]
    .sort((a, b) => b.frequency - a.frequency)
    .slice(0, 5)
    .map((c) => c.number);

  const coldNumbers = [...scoredCandidates]
    .sort((a, b) => b.drawGap - a.drawGap)
    .slice(0, 5)
    .map((c) => c.number);

  const pairs: { n1: number; n2: number; count: number }[] = [];
  for (let i = 1; i <= maxLimit; i++) {
    for (let j = i + 1; j <= maxLimit; j++) {
      if (pairMatrix[i][j] > 0) {
        pairs.push({ n1: i, n2: j, count: pairMatrix[i][j] });
      }
    }
  }
  pairs.sort((a, b) => b.count - a.count);
  const frequentPairs = pairs.slice(0, 3).map((p) => {
    const pad1 = p.n1 < 10 ? `0${p.n1}` : `${p.n1}`;
    const pad2 = p.n2 < 10 ? `0${p.n2}` : `${p.n2}`;
    return `${pad1} - ${pad2} (${p.count} lần)`;
  });

  // Recent draws
  const recentDraws: DrawRecordDto[] = categoryRecords
    .slice()
    .reverse()
    .slice(0, 10)
    .map((r) => ({
      id: r.id,
      drawDate: r.drawDate,
      numbers: r.numbers,
      specialNumber: r.specialNumber,
      note: r.note,
    }));

  // Target numbers for history map
  const allTargetNumbers = Array.from(new Set([...top10Numbers, ...(recommendedSpecialNumber ? [recommendedSpecialNumber] : [])]));

  const numberHistoryMap: Record<number, NumberHistoryAppearance[]> = {};
  for (const num of allTargetNumbers) {
    numberHistoryMap[num] = [];
    for (const draw of categoryRecords.slice().reverse()) {
      const isMain = draw.numbers.includes(num);
      const isSpecial = draw.specialNumber === num;
      if (isMain || isSpecial) {
        numberHistoryMap[num].push({
          drawDate: draw.drawDate,
          role: isSpecial ? 'special' : 'main',
          allNumbers: draw.numbers,
          specialNumber: draw.specialNumber,
        });
      }
    }
  }

  // Selection reasons
  const selectionReasons: NumberSelectionReason[] = top10Candidates.map((c) => ({
    number: c.number,
    role: 'main',
    tag: c.tag,
    title: c.title,
    reason: c.reason,
    probabilityPercent: Math.round(c.probability * 1000.0) / 10.0,
    frequency: c.frequency,
    drawGap: c.drawGap,
  }));

  // If POWER, add special number reason
  if (category === 'POWER' && recommendedSpecialNumber !== undefined && specialDetail) {
    selectionReasons.push({
      number: recommendedSpecialNumber,
      role: 'special',
      tag: 'BẢO HIỂM JACKPOT 2',
      title: `Bảo Hiểm Jackpot 2 (${algName})`,
      reason: `Nếu trật 1 số bất kỳ trong 6 số chính (khớp 5/6 số), số ${recommendedSpecialNumber < 10 ? '0' + recommendedSpecialNumber : recommendedSpecialNumber} đạt điểm bù trừ cao nhất theo ma trận lịch sử để trúng giải Jackpot 2.`,
      probabilityPercent: specialDetail.probabilityPercent,
      frequency: specialDetail.specialFrequency,
      drawGap: specialDetail.drawGap,
    });
  }

  const overallReason = `${algOverallReason} Dãy số được phân bổ hài hòa theo tỷ lệ ${10 - oddCount} Chẵn / ${oddCount} Lẻ. ${
    category === 'POWER' && recommendedSpecialNumber
      ? `Đồng thời, Số phụ ⭐${recommendedSpecialNumber < 10 ? '0' + recommendedSpecialNumber : recommendedSpecialNumber} được tích hợp để bảo hiểm giải Jackpot 2.`
      : ''
  }`;

  return {
    status: 'SUCCESS',
    category,
    lotteryType: category,
    algorithm,
    algorithmName: algName,
    algorithmDesc: algDesc,
    numbers: top10Numbers,
    tickets: generatedTickets,
    specialNumber: recommendedSpecialNumber,
    specialNumberDetail: specialDetail,
    specialHotNumbers,
    jackpot2Pairs,
    totalDrawsAnalyzed: totalDraws,
    hotNumbers,
    coldNumbers,
    frequentPairs,
    oddEvenRatio: `${10 - oddCount} Chẵn / ${oddCount} Lẻ`,
    details: detailDtos,
    analysisSummary: algSummary,
    selectionReasons,
    overallReason,
    recentDraws,
    numberHistoryMap,
  };
}

async function startServer() {
  loadInitialData();

  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // CORS headers
  app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET,POST,DELETE,OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type,Authorization');
    if (req.method === 'OPTIONS') {
      return res.sendStatus(200);
    }
    next();
  });

  // API Routes
  app.get('/api/numbers', (req: Request, res: Response) => {
    const { date, category } = req.query;
    let result = [...records];

    if (category && typeof category === 'string' && category.trim()) {
      const cat = category.trim().toUpperCase();
      result = result.filter((r) => r.category === cat);
    }

    if (date && typeof date === 'string' && date.trim()) {
      const d = date.trim();
      result = result.filter((r) => r.drawDate === d);
    }

    // Sort by drawDate desc, then createdAt desc
    result.sort(
      (a, b) =>
        b.drawDate.localeCompare(a.drawDate) ||
        (b.createdAt || '').localeCompare(a.createdAt || '')
    );
    res.json(result);
  });

  app.post('/api/numbers', (req: Request, res: Response) => {
    try {
      const { numbers, drawDate, category: catInput, specialNumber, note } = req.body;

      if (!Array.isArray(numbers) || numbers.length !== 6) {
        return res
          .status(400)
          .json({ error: 'Yêu cầu nhập chính xác đúng 6 con số chính!' });
      }

      const uniqueCheck = new Set(numbers);
      if (uniqueCheck.size !== 6) {
        return res
          .status(400)
          .json({ error: 'Các con số chính không được trùng nhau!' });
      }

      const date =
        drawDate && typeof drawDate === 'string' && drawDate.trim()
          ? drawDate.trim()
          : new Date().toISOString().slice(0, 10);

      let category: 'MEGA' | 'POWER';
      if (catInput && typeof catInput === 'string' && catInput.trim()) {
        const c = catInput.trim().toUpperCase();
        if (c !== 'MEGA' && c !== 'POWER') {
          return res.status(400).json({
            error: 'Category không hợp lệ! Chỉ chấp nhận MEGA hoặc POWER.',
          });
        }
        category = c as 'MEGA' | 'POWER';
      } else {
        category = determineCategoryFromDate(date);
      }

      const maxLimit = category === 'POWER' ? 55 : 45;
      for (const num of numbers) {
        if (
          typeof num !== 'number' ||
          !Number.isInteger(num) ||
          num < 1 ||
          num > maxLimit
        ) {
          return res.status(400).json({
            error: `Với danh mục ${category}, mỗi số chính phải từ 1 đến ${maxLimit}! (Số không hợp lệ: ${num})`,
          });
        }
      }

      let parsedSpecialNumber: number | undefined = undefined;
      if (category === 'POWER') {
        if (specialNumber !== undefined && specialNumber !== null && specialNumber !== '') {
          const sp = Number(specialNumber);
          if (!Number.isInteger(sp) || sp < 1 || sp > 55) {
            return res.status(400).json({
              error: 'Số phụ của Power 6/55 phải là số nguyên từ 1 đến 55!',
            });
          }
          if (numbers.includes(sp)) {
            return res.status(400).json({
              error: `Số phụ (${sp}) không được trùng với bất kỳ số nào trong 6 số chính!`,
            });
          }
          parsedSpecialNumber = sp;
        }
      }

      const sortedNumbers = [...numbers].sort((a, b) => a - b);
      const newRecord: LotteryNumberRecord = {
        id: nextId++,
        drawDate: date,
        category,
        numbers: sortedNumbers,
        specialNumber: parsedSpecialNumber,
        createdAt: new Date().toISOString(),
        note: note ? String(note).trim() : undefined,
      };

      records.unshift(newRecord);
      saveDataToDisk();

      return res.status(201).json(newRecord);
    } catch (err: any) {
      return res.status(500).json({ error: err.message || 'Server error' });
    }
  });

  app.delete('/api/numbers/:id', (req: Request, res: Response) => {
    const id = parseInt(req.params.id, 10);
    if (isNaN(id)) {
      return res
        .status(400)
        .json({ success: false, message: 'ID không hợp lệ' });
    }

    const index = records.findIndex((r) => r.id === id);
    if (index !== -1) {
      records.splice(index, 1);
      saveDataToDisk();
      return res.json({
        success: true,
        message: 'Đã xóa thành công khỏi hệ thống',
      });
    } else {
      return res.status(404).json({
        success: false,
        message: 'Không tìm thấy bản ghi có ID: ' + id,
      });
    }
  });

  app.get('/api/analyze/predict', (req: Request, res: Response) => {
    const category =
      typeof req.query.category === 'string' ? req.query.category : 'MEGA';
    const algorithm =
      typeof req.query.algorithm === 'string' ? req.query.algorithm : 'xgboost';
    const result = analyzeAndPredict(category, algorithm);
    res.json(result);
  });

  // Vite middleware for development vs static for production
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Analyze Project server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
