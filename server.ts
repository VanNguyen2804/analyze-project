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
  numbers: number[];
  createdAt: string;
  note?: string;
}

const DATA_DIR = path.join(process.cwd(), 'data');
const DB_FILE = path.join(DATA_DIR, 'h2_lottery_numbers.json');

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
    lotteryNumbersTable = JSON.parse(raw);
    const maxId = lotteryNumbersTable.reduce((max, r) => Math.max(max, r.id), 0);
    nextId = maxId + 1;
  } else {
    // Initial sample record in H2 DB
    lotteryNumbersTable = [
      {
        id: 1,
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

// GET /api/numbers - Retrieve all saved lottery numbers
app.get('/api/numbers', (req, res) => {
  // Return sorted descending by createdAt
  const sorted = [...lotteryNumbersTable].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );
  res.json(sorted);
});

// POST /api/numbers - Save 6 manually entered numbers into H2 DB
app.post('/api/numbers', (req, res) => {
  const { numbers, note } = req.body;

  if (!Array.isArray(numbers) || numbers.length !== 6) {
    return res.status(400).json({ error: 'Yêu cầu nhập chính xác đúng 6 con số!' });
  }

  // Check unique numbers
  const uniqueSet = new Set(numbers);
  if (uniqueSet.size !== 6) {
    return res.status(400).json({ error: 'Các con số không được trùng nhau!' });
  }

  // Check number ranges (1 to 45)
  for (const n of numbers) {
    const num = Number(n);
    if (!Number.isInteger(num) || num < 1 || num > 45) {
      return res
        .status(400)
        .json({ error: `Số ${n} không hợp lệ! Vui lòng chỉ nhập các số từ 1 đến 45.` });
    }
  }

  const sortedNumbers = [...numbers].map(Number).sort((a, b) => a - b);
  const newRecord: LotteryRecord = {
    id: nextId++,
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

// Simulation of XGBoost lottery prediction model (1 to 45, select top 6 sorted)
function predictNumbers(): number[] {
  // Generate probabilities for 45 numbers using simulated logistic features
  const candidates: { number: number; score: number }[] = [];

  for (let i = 1; i <= 45; i++) {
    // Feature weights simulation
    const feature1 = Math.random();
    const feature2 = Math.random();
    // Logistic log-odds
    const z = feature1 * 1.5 - feature2 * 0.8 + (Math.sin(i) * 0.2);
    const prob = 1 / (1 + Math.exp(-z));
    candidates.push({ number: i, score: prob });
  }

  // Sort by highest probability and take top 6
  candidates.sort((a, b) => b.score - a.score);
  const top6 = candidates.slice(0, 6).map(c => c.number);

  // Sort ascending like in XGBoost script
  top6.sort((a, b) => a - b);
  return top6;
}

// Fallback random prediction (matching Java AnalyzeService fallback)
function fallbackRandomPredict(): number[] {
  const numbers = new Set<number>();
  while (numbers.size < 6) {
    numbers.add(Math.floor(Math.random() * 45) + 1);
  }
  return Array.from(numbers).sort((a, b) => a - b);
}

// REST API matching Spring Boot AnalyzeController: @GetMapping("/predict")
app.get('/api/analyze/predict', (req, res) => {
  try {
    const results = predictNumbers();
    res.json(results);
  } catch (error) {
    console.error('Error during prediction:', error);
    res.json(fallbackRandomPredict());
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
