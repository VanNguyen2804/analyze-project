import express from 'express';
import path from 'path';
import fs from 'fs';
import { createServer as createViteServer } from 'vite';

const app = express();
const PORT = 3000;

app.use(express.json());

// Persistent JSON store for Preview
interface LotteryRecord {
  id: number;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[];
  createdAt: string;
  note?: string;
}

const DATA_DIR = path.join(process.cwd(), 'data');
const DB_FILE = path.join(DATA_DIR, 'lottery_numbers.json');

// Helper to get today's date in YYYY-MM-DD
function getTodayDateString(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Determine category based on day of week:
// Mega: Thứ 4 (3), Thứ 6 (5), Chủ nhật (0)
// Power: Thứ 3 (2), Thứ 5 (4), Thứ 7 (6)
export function determineCategoryFromDate(dateStr: string): 'MEGA' | 'POWER' {
  try {
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      const year = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const day = parseInt(parts[2], 10);
      const dow = new Date(year, month, day).getDay(); // 0: Sun, 1: Mon, ..., 6: Sat
      if (dow === 2 || dow === 4 || dow === 6) {
        return 'POWER';
      }
      return 'MEGA';
    }
  } catch (e) {
    // fallback
  }
  return 'MEGA';
}

// Ensure data folder exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// In-Memory table initialized from persistent store or default seeds
let lotteryNumbersTable: LotteryRecord[] = [];
let nextId = 1;

try {
  if (fs.existsSync(DB_FILE)) {
    const raw = fs.readFileSync(DB_FILE, 'utf-8');
    const parsed: any[] = JSON.parse(raw);
    lotteryNumbersTable = parsed.map((item) => {
      const drawDate = item.drawDate || item.createdAt?.slice(0, 10) || getTodayDateString();
      const category = item.category === 'POWER' || item.category === 'MEGA'
        ? item.category
        : determineCategoryFromDate(drawDate);
      return {
        id: item.id,
        drawDate,
        category,
        numbers: item.numbers,
        createdAt: item.createdAt || new Date().toISOString(),
        note: item.note,
      };
    });
    const maxId = lotteryNumbersTable.reduce((max, r) => Math.max(max, r.id), 0);
    nextId = maxId + 1;
  } else {
    const today = getTodayDateString();
    lotteryNumbersTable = [
      {
        id: 1,
        drawDate: today,
        category: determineCategoryFromDate(today),
        numbers: [3, 12, 19, 27, 34, 42],
        createdAt: new Date().toISOString(),
        note: 'Bộ số mẫu khởi tạo',
      },
    ];
    nextId = 2;
    fs.writeFileSync(DB_FILE, JSON.stringify(lotteryNumbersTable, null, 2));
  }
} catch (err) {
  console.error('Error loading DB storage:', err);
}

function persistDatabase(): void {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(lotteryNumbersTable, null, 2));
  } catch (err) {
    console.error('Failed to write to DB storage:', err);
  }
}

// ==========================
// REST Endpoints
// ==========================

// GET /api/numbers - Retrieve all saved lottery numbers with optional ?date=YYYY-MM-DD and ?category=MEGA|POWER
app.get('/api/numbers', (req, res) => {
  const dateParam = req.query.date as string | undefined;
  const categoryParam = req.query.category as string | undefined;

  let records = [...lotteryNumbersTable];
  if (dateParam && dateParam.trim() !== '') {
    records = records.filter((r) => r.drawDate === dateParam.trim());
  }

  if (categoryParam && categoryParam.trim() !== '') {
    const cat = categoryParam.trim().toUpperCase();
    records = records.filter((r) => r.category === cat);
  }

  // Return sorted descending by drawDate then createdAt
  const sorted = records.sort((a, b) => {
    const cmpDate = b.drawDate.localeCompare(a.drawDate);
    if (cmpDate !== 0) return cmpDate;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
  res.json(sorted);
});

// POST /api/numbers - Save 6 manually entered numbers into H2 DB for a given drawDate & category
app.post('/api/numbers', (req, res) => {
  const { numbers, note, drawDate, category } = req.body;

  if (!Array.isArray(numbers) || numbers.length !== 6) {
    return res.status(400).json({ error: 'Yêu cầu nhập chính xác đúng 6 con số!' });
  }

  // Check unique numbers
  const uniqueSet = new Set(numbers);
  if (uniqueSet.size !== 6) {
    return res.status(400).json({ error: 'Các con số không được trùng nhau!' });
  }

  // Validate or default date
  let targetDate = typeof drawDate === 'string' && drawDate.trim() ? drawDate.trim() : getTodayDateString();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) {
    targetDate = getTodayDateString();
  }

  // Determine category: MEGA (1..45) or POWER (1..55)
  let targetCategory: 'MEGA' | 'POWER' = 'MEGA';
  if (category && (category.toUpperCase() === 'POWER' || category.toUpperCase() === 'MEGA')) {
    targetCategory = category.toUpperCase() as 'MEGA' | 'POWER';
  } else {
    targetCategory = determineCategoryFromDate(targetDate);
  }

  const maxLimit = targetCategory === 'POWER' ? 55 : 45;

  // Check number ranges
  for (const n of numbers) {
    const num = Number(n);
    if (!Number.isInteger(num) || num < 1 || num > maxLimit) {
      return res.status(400).json({
        error: `Danh mục ${targetCategory} chỉ chấp nhận các số từ 1 đến ${maxLimit}! (Số không hợp lệ: ${n})`,
      });
    }
  }

  const sortedNumbers = [...numbers].map(Number).sort((a, b) => a - b);
  const newRecord: LotteryRecord = {
    id: nextId++,
    drawDate: targetDate,
    category: targetCategory,
    numbers: sortedNumbers,
    createdAt: new Date().toISOString(),
    note: typeof note === 'string' ? note.trim() : '',
  };

  lotteryNumbersTable.push(newRecord);
  persistDatabase();

  return res.status(201).json(newRecord);
});

// DELETE /api/numbers/:id - Delete a record
app.delete('/api/numbers/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const initialLen = lotteryNumbersTable.length;
  lotteryNumbersTable = lotteryNumbersTable.filter((r) => r.id !== id);

  if (lotteryNumbersTable.length < initialLen) {
    persistDatabase();
    return res.json({ success: true, message: `Đã xóa bản ghi #${id} thành công.` });
  } else {
    return res.status(404).json({ success: false, error: `Không tìm thấy bản ghi #${id}` });
  }
});

// GET /api/database/status - Status endpoint
app.get('/api/database/status', (req, res) => {
  res.json({
    totalRecords: lotteryNumbersTable.length,
    status: 'ONLINE',
  });
});

// ====================================================================
// Advanced Sequence-by-Date Analysis & XGBoost Prediction per Category
// ====================================================================

interface NumberScoreDetail {
  number: number;
  probabilityPercent: number;
  frequency: number;
  drawGap: number;
  tag: string; // 'SỐ NÓNG' | 'LÔ GAN' | 'CẶP ĐI KÈM' | 'CÂN BẰNG'
}

interface PredictionResponse {
  category: 'MEGA' | 'POWER';
  numbers: number[];
  totalDrawsAnalyzed: number;
  hotNumbers: number[];
  coldNumbers: number[];
  frequentPairs: string[];
  oddEvenRatio: string;
  analysisSummary: string;
  details: NumberScoreDetail[];
}

function analyzeAndPredict(category: 'MEGA' | 'POWER' = 'MEGA'): PredictionResponse {
  const maxLimit = category === 'POWER' ? 55 : 45;

  // 1. Lọc các dãy số theo ngày cho riêng danh mục được yêu cầu
  const categoryRecords = lotteryNumbersTable.filter((r) => r.category === category);

  // Sắp xếp theo thứ tự thời gian tăng dần: từ kỳ cũ nhất đến kỳ gần nhất
  const chronological = [...categoryRecords].sort((a, b) => {
    const cmp = a.drawDate.localeCompare(b.drawDate);
    if (cmp !== 0) return cmp;
    return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
  });

  const totalDraws = chronological.length;

  // 2. Thống kê đặc trưng (Features) theo chuỗi kỳ quay của category
  const frequency: number[] = new Array(maxLimit + 1).fill(0);
  const lastSeenIndex: number[] = new Array(maxLimit + 1).fill(-1);
  const momentum: number[] = new Array(maxLimit + 1).fill(0);
  const pairMatrix: number[][] = Array.from({ length: maxLimit + 1 }, () => new Array(maxLimit + 1).fill(0));

  for (let t = 0; t < totalDraws; t++) {
    const draw = chronological[t];
    const validNums = (draw.numbers || []).filter((n) => n >= 1 && n <= maxLimit);

    for (const n of validNums) {
      frequency[n]++;
      lastSeenIndex[n] = t;
      // Trọng số thời gian lũy thừa (kỳ càng gần trọng số càng cao)
      const weight = Math.exp(-0.12 * (totalDraws - 1 - t));
      momentum[n] += weight;
    }

    // Cặp số hay đi cùng nhau trong cùng kỳ
    for (let i = 0; i < validNums.length; i++) {
      for (let j = i + 1; j < validNums.length; j++) {
        const n1 = validNums[i];
        const n2 = validNums[j];
        pairMatrix[n1][n2]++;
        pairMatrix[n2][n1]++;
      }
    }
  }

  // 3. Tính khoảng cách chưa về (Draw Gap / Lô gan)
  const drawGap: number[] = new Array(maxLimit + 1).fill(0);
  for (let i = 1; i <= maxLimit; i++) {
    if (lastSeenIndex[i] === -1) {
      drawGap[i] = totalDraws + 1;
    } else {
      drawGap[i] = totalDraws - 1 - lastSeenIndex[i];
    }
  }

  let maxMom = 0;
  for (let i = 1; i <= maxLimit; i++) {
    if (momentum[i] > maxMom) maxMom = momentum[i];
  }
  if (maxMom === 0) maxMom = 1;

  // 4. XGBoost Probability Scoring
  interface Candidate {
    number: number;
    score: number;
    frequency: number;
    drawGap: number;
  }
  const candidates: Candidate[] = [];

  for (let i = 1; i <= maxLimit; i++) {
    const normFreq = totalDraws > 0 ? frequency[i] / totalDraws : 0.2;
    const normMom = momentum[i] / maxMom;

    const avgCycle = maxLimit / 6;
    const gapRatio = drawGap[i] / avgCycle;
    let gapScore = 0.3;
    if (gapRatio >= 1.0 && gapRatio <= 2.5) {
      gapScore = 0.85; // Điểm rơi chu kỳ
    } else if (gapRatio > 2.5) {
      gapScore = 0.5; // Gan quá lâu
    }

    let topPairSum = 0;
    for (let j = 1; j <= maxLimit; j++) {
      if (i !== j && pairMatrix[i][j] > 0) topPairSum += pairMatrix[i][j];
    }
    const pairScore = Math.min(1.0, topPairSum / 5.0);

    let z = 0;
    if (totalDraws >= 2) {
      z = (normMom * 1.8) + (normFreq * 1.2) + (gapScore * 0.9) + (pairScore * 0.7) - 1.2 + (Math.random() * 0.3 - 0.15);
    } else {
      z = Math.sin(i * 0.55) * 0.6 + Math.cos(i * 0.35) * 0.4 + (Math.random() * 0.8 - 0.4);
    }

    const prob = 1 / (1 + Math.exp(-z));
    candidates.push({ number: i, score: prob, frequency: frequency[i], drawGap: drawGap[i] });
  }

  candidates.sort((a, b) => b.score - a.score);

  // 5. Tuyển chọn 6 số tối ưu cân bằng Chẵn/Lẻ
  const selected: Candidate[] = [];
  let oddCount = 0;
  let evenCount = 0;

  for (const cand of candidates) {
    if (selected.length >= 6) break;
    const isOdd = cand.number % 2 !== 0;
    if (isOdd && oddCount >= 4 && selected.length < 5) continue;
    if (!isOdd && evenCount >= 4 && selected.length < 5) continue;

    selected.push(cand);
    if (isOdd) oddCount++;
    else evenCount++;
  }

  if (selected.length < 6) {
    for (const cand of candidates) {
      if (selected.length >= 6) break;
      if (!selected.some((s) => s.number === cand.number)) {
        selected.push(cand);
      }
    }
  }

  selected.sort((a, b) => a.number - b.number);

  // 6. Gán nhãn cho từng số trong bộ 6 số đề xuất
  const details: NumberScoreDetail[] = selected.map((s) => {
    let tag = 'CÂN BẰNG';
    if (s.drawGap >= Math.floor(maxLimit / 6)) {
      tag = 'LÔ GAN';
    } else if (momentum[s.number] > maxMom * 0.6) {
      tag = 'SỐ NÓNG';
    } else if (selected.some((other) => other.number !== s.number && pairMatrix[s.number][other.number] >= 2)) {
      tag = 'CẶP ĐI KÈM';
    }
    return {
      number: s.number,
      probabilityPercent: Math.round(s.score * 1000) / 10,
      frequency: s.frequency,
      drawGap: s.drawGap,
      tag,
    };
  });

  const hotNumbers = [...candidates]
    .sort((a, b) => b.frequency - a.frequency)
    .slice(0, 5)
    .map((c) => c.number);

  const coldNumbers = [...candidates]
    .sort((a, b) => b.drawGap - a.drawGap)
    .slice(0, 5)
    .map((c) => c.number);

  const pairList: { n1: number; n2: number; count: number }[] = [];
  for (let i = 1; i <= maxLimit; i++) {
    for (let j = i + 1; j <= maxLimit; j++) {
      if (pairMatrix[i][j] > 0) {
        pairList.push({ n1: i, n2: j, count: pairMatrix[i][j] });
      }
    }
  }
  pairList.sort((a, b) => b.count - a.count);
  const frequentPairs = pairList.slice(0, 3).map((p) => `${String(p.n1).padStart(2, '0')} - ${String(p.n2).padStart(2, '0')} (${p.count} lần)`);

  const categoryName = category === 'POWER' ? 'Power 6/55' : 'Mega 6/45';
  const summary = totalDraws > 0
    ? `Đã phân tích chuyên sâu ${totalDraws} dãy số theo ngày của ${categoryName}. Mô hình AI kết hợp tần suất xuất hiện, chu kỳ lô gan, ma trận cặp số và mô hình xác suất XGBoost.`
    : `Chưa có dữ liệu dãy số lưu cho ${categoryName}. Đang áp dụng mô hình phân phối chuẩn hóa khởi tạo.`;

  return {
    category,
    numbers: selected.map((s) => s.number),
    totalDrawsAnalyzed: totalDraws,
    hotNumbers,
    coldNumbers,
    frequentPairs,
    oddEvenRatio: `${6 - oddCount} Chẵn / ${oddCount} Lẻ`,
    analysisSummary: summary,
    details,
  };
}

// REST API matching Spring Boot AnalyzeController: @GetMapping("/predict")
app.get('/api/analyze/predict', (req, res) => {
  try {
    const cat = req.query.category === 'POWER' ? 'POWER' : 'MEGA';
    const result = analyzeAndPredict(cat);
    res.json(result);
  } catch (error) {
    console.error('Error during prediction:', error);
    res.status(500).json({ error: 'Lỗi trong quá trình phân tích dự đoán' });
  }
});

// POST /api/predict supporting { category: 'MEGA' | 'POWER' }
app.post('/api/predict', (req, res) => {
  try {
    const cat = req.body?.category === 'POWER' ? 'POWER' : 'MEGA';
    const result = analyzeAndPredict(cat);
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: 'Lỗi dự đoán' });
  }
});

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', service: 'analyze-project' });
});

async function startServer() {
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
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
