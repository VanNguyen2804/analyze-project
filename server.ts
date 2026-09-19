import express from 'express';
import path from 'path';
import fs from 'fs';
import { createServer as createViteServer } from 'vite';

const app = express();
const PORT = 3000;

app.use(express.json());

// In-Memory H2 Database Simulation (matching Spring Boot H2: jdbc:h2:mem:lotterydb)
interface LotteryRecord {
  id: number;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[];
  createdAt: string;
  note?: string;
}

const DATA_DIR = path.join(process.cwd(), 'data');
const DB_FILE = path.join(DATA_DIR, 'h2_lottery_numbers.json');

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
        note: 'Bộ số mẫu khởi tạo (DB H2)',
      },
    ];
    nextId = 2;
    fs.writeFileSync(DB_FILE, JSON.stringify(lotteryNumbersTable, null, 2));
  }
} catch (err) {
  console.error('Error loading H2 DB storage:', err);
}

function persistH2Database(): void {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(lotteryNumbersTable, null, 2));
  } catch (err) {
    console.error('Failed to write to H2 DB storage:', err);
  }
}

// ==========================
// H2 Database REST Endpoints
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
  persistH2Database();

  return res.status(201).json(newRecord);
});

// DELETE /api/numbers/:id - Delete a record from H2 DB
app.delete('/api/numbers/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const initialLen = lotteryNumbersTable.length;
  lotteryNumbersTable = lotteryNumbersTable.filter((r) => r.id !== id);

  if (lotteryNumbersTable.length < initialLen) {
    persistH2Database();
    return res.json({ success: true, message: `Đã xóa bản ghi #${id} khỏi DB H2.` });
  } else {
    return res.status(404).json({ success: false, error: `Không tìm thấy bản ghi #${id}` });
  }
});

// GET /api/database/status - Info about H2 Database status
app.get('/api/database/status', (req, res) => {
  res.json({
    database: 'H2 In-Memory Database',
    url: 'jdbc:h2:mem:lotterydb',
    table: 'LOTTERY_NUMBERS',
    totalRecords: lotteryNumbersTable.length,
    status: 'ONLINE',
  });
});

// Simulation of XGBoost lottery prediction model (1 to 45 for MEGA, 1 to 55 for POWER)
function predictNumbers(category: 'MEGA' | 'POWER' = 'MEGA'): number[] {
  const maxLimit = category === 'POWER' ? 55 : 45;
  const candidates: { number: number; score: number }[] = [];

  for (let i = 1; i <= maxLimit; i++) {
    const feature1 = Math.random();
    const feature2 = Math.random();
    const z = feature1 * 1.5 - feature2 * 0.8 + (Math.sin(i) * 0.25);
    const prob = 1 / (1 + Math.exp(-z));
    candidates.push({ number: i, score: prob });
  }

  candidates.sort((a, b) => b.score - a.score);
  const top6 = candidates.slice(0, 6).map(c => c.number);
  top6.sort((a, b) => a - b);
  return top6;
}

function fallbackRandomPredict(category: 'MEGA' | 'POWER' = 'MEGA'): number[] {
  const maxLimit = category === 'POWER' ? 55 : 45;
  const numbers = new Set<number>();
  while (numbers.size < 6) {
    numbers.add(Math.floor(Math.random() * maxLimit) + 1);
  }
  return Array.from(numbers).sort((a, b) => a - b);
}

// REST API matching Spring Boot AnalyzeController: @GetMapping("/predict")
app.get('/api/analyze/predict', (req, res) => {
  try {
    const cat = req.query.category === 'POWER' ? 'POWER' : 'MEGA';
    const results = predictNumbers(cat);
    res.json(results);
  } catch (error) {
    console.error('Error during prediction:', error);
    res.json(fallbackRandomPredict());
  }
});

// POST /api/predict supporting { category: 'MEGA' | 'POWER' }
app.post('/api/predict', (req, res) => {
  try {
    const cat = req.body?.category === 'POWER' ? 'POWER' : 'MEGA';
    const numbers = predictNumbers(cat);
    res.json({ numbers, category: cat });
  } catch (error) {
    res.json({ numbers: fallbackRandomPredict(), category: 'MEGA' });
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
