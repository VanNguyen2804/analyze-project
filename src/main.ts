// Pure TypeScript Frontend for Analyze Project

interface SavedRecord {
  id: number;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[]; // 6 distinct numbers
  specialNumber?: number; // Added for category POWER
  createdAt: string;
  note?: string;
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

interface PredictionResponse {
  category: 'MEGA' | 'POWER';
  numbers: number[];
  specialNumber?: number;
  specialNumberDetail?: SpecialNumberDetail;
  specialHotNumbers?: number[];
  jackpot2Pairs?: string[];
  totalDrawsAnalyzed: number;
  hotNumbers: number[];
  coldNumbers: number[];
  frequentPairs: string[];
  oddEvenRatio: string;
  details: NumberScoreDetail[];
  analysisSummary: string;
  selectionReasons?: NumberSelectionReason[];
  overallReason?: string;
  recentDraws?: DrawRecordDto[];
  numberHistoryMap?: Record<number, NumberHistoryAppearance[]>;
}

// App State
let activeTab: 'manual' | 'prediction' = 'prediction'; // Default or prediction
let selectedCategory: 'MEGA' | 'POWER' = 'POWER';
let selectedDate: string = getTodayDateString();
let selectedNumbers: number[] = [];
let selectedSpecialNumber: number | null = null;
let activeSelectionTarget: 'main' | 'special' = 'main';
let noteText: string = '';
let isSaving: boolean = false;
let savedRecords: SavedRecord[] = [];
let filterDate: string = '';
let filterCategory: string = '';

// Prediction State
let predictionCategory: 'MEGA' | 'POWER' = 'POWER';
let predictedNumbers: number[] = [];
let predictedSpecialNumber: number | null = null;
let predictionResultData: PredictionResponse | null = null;
let isPredicting: boolean = false;
let predictSaveSuccess: string | null = null;
let statusMessage: { type: 'success' | 'danger' | 'warning'; text: string } | null = null;

// History View State in Prediction Tab
let historyLimit: number = 10;
let historyFilterNumber: number | null = null; // null = all recommended numbers

function getTodayDateString(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getYesterdayDateString(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getCategoryFromDate(dateStr: string): 'MEGA' | 'POWER' {
  if (!dateStr) return 'POWER';
  const parts = dateStr.split('-').map(Number);
  if (parts.length === 3) {
    const dateObj = new Date(parts[0], parts[1] - 1, parts[2]);
    const day = dateObj.getDay();
    if (day === 2 || day === 4 || day === 6) {
      return 'POWER';
    }
  }
  return 'MEGA';
}

function getDayOfWeekName(dateStr: string): string {
  if (!dateStr) return '';
  const parts = dateStr.split('-').map(Number);
  if (parts.length !== 3) return '';
  const dateObj = new Date(parts[0], parts[1] - 1, parts[2]);
  const day = dateObj.getDay();
  const names = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
  return names[day] || '';
}

function getMaxLimit(): number {
  return selectedCategory === 'POWER' ? 55 : 45;
}

// Data Fetching
async function fetchSavedRecords(): Promise<void> {
  try {
    const params = new URLSearchParams();
    if (filterDate.trim()) params.set('date', filterDate.trim());
    if (filterCategory.trim()) params.set('category', filterCategory.trim());

    const url = params.toString() ? `/api/numbers?${params.toString()}` : '/api/numbers';
    const res = await fetch(url);
    if (res.ok) {
      savedRecords = await res.json();
      render();
    }
  } catch (err) {
    console.error('Lỗi khi tải dữ liệu:', err);
  }
}

async function saveToH2(): Promise<void> {
  statusMessage = null;
  if (selectedNumbers.length !== 6) {
    statusMessage = {
      type: 'danger',
      text: `Vui lòng chọn đủ 6 số chính khác nhau! (Hiện tại: ${selectedNumbers.length}/6 số)`,
    };
    render();
    return;
  }

  if (selectedCategory === 'POWER' && selectedSpecialNumber !== null) {
    if (selectedNumbers.includes(selectedSpecialNumber)) {
      statusMessage = {
        type: 'danger',
        text: `Số phụ (${selectedSpecialNumber}) không được trùng với bất kỳ số nào trong 6 số chính!`,
      };
      render();
      return;
    }
  }

  isSaving = true;
  render();

  try {
    const res = await fetch('/api/numbers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        numbers: selectedNumbers,
        specialNumber: selectedCategory === 'POWER' ? selectedSpecialNumber : undefined,
        drawDate: selectedDate,
        category: selectedCategory,
        note: noteText.trim(),
      }),
    });

    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.error || 'Lỗi khi lưu bộ số');
    }

    const saved: SavedRecord = await res.json();
    const specialInfo = saved.specialNumber ? ` + Số phụ ⭐ ${saved.specialNumber < 10 ? '0' + saved.specialNumber : saved.specialNumber}` : '';
    statusMessage = {
      type: 'success',
      text: `Đã lưu thành công bộ số ${saved.category}${specialInfo} cho ngày ${saved.drawDate} (${getDayOfWeekName(saved.drawDate)}) (#${saved.id})!`,
    };
    selectedNumbers = [];
    selectedSpecialNumber = null;
    activeSelectionTarget = 'main';
    noteText = '';
    await fetchSavedRecords();
  } catch (err: any) {
    statusMessage = { type: 'danger', text: err.message || 'Lỗi lưu dữ liệu.' };
  } finally {
    isSaving = false;
    render();
  }
}

async function deleteRecord(id: number): Promise<void> {
  if (!confirm(`Bạn có chắc muốn xóa bản ghi #${id} không?`)) return;

  try {
    const res = await fetch(`/api/numbers/${id}`, { method: 'DELETE' });
    if (res.ok) {
      savedRecords = savedRecords.filter(r => r.id !== id);
      statusMessage = { type: 'success', text: `Đã xóa bản ghi #${id} thành công.` };
      render();
    }
  } catch (err) {
    console.error('Lỗi khi xóa bản ghi:', err);
  }
}

async function runPrediction(): Promise<void> {
  isPredicting = true;
  predictSaveSuccess = null;
  render();

  try {
    const res = await fetch(`/api/analyze/predict?category=${predictionCategory}`);
    if (res.ok) {
      const data: PredictionResponse = await res.json();
      predictionResultData = data;
      predictedNumbers = data.numbers || [];
      predictedSpecialNumber = data.specialNumber ?? null;
      historyLimit = 10;
      historyFilterNumber = null;
    }
  } catch (err) {
    console.warn('Backend error or simulation fallback:', err);
  } finally {
    isPredicting = false;
    render();
  }
}

async function savePredictedToH2(): Promise<void> {
  if (predictedNumbers.length !== 6) return;

  try {
    const res = await fetch('/api/numbers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        numbers: predictedNumbers,
        specialNumber: predictionCategory === 'POWER' ? predictedSpecialNumber : undefined,
        drawDate: selectedDate,
        category: predictionCategory,
        note: `Dự đoán AI XGBoost (${predictionCategory === 'MEGA' ? 'Mega 6/45' : 'Power 6/55'} - Ngày ${selectedDate})`,
      }),
    });
    if (res.ok) {
      const saved: SavedRecord = await res.json();
      const spText = saved.specialNumber ? ` (kèm Số phụ ⭐ ${saved.specialNumber < 10 ? '0' + saved.specialNumber : saved.specialNumber})` : '';
      predictSaveSuccess = `Đã lưu bộ số dự đoán ${saved.category}${spText} cho ngày ${saved.drawDate} (#${saved.id}) vào cơ sở dữ liệu!`;
      await fetchSavedRecords();
    }
  } catch (err) {
    console.error('Lỗi khi lưu bộ số dự đoán:', err);
  } finally {
    render();
  }
}

function toggleNumber(num: number): void {
  statusMessage = null;

  if (activeSelectionTarget === 'special') {
    if (selectedNumbers.includes(num)) {
      statusMessage = {
        type: 'warning',
        text: `Số ${num} đã nằm trong 6 số chính! Số phụ phải khác với 6 số chính.`,
      };
      render();
      return;
    }
    if (selectedSpecialNumber === num) {
      selectedSpecialNumber = null;
    } else {
      selectedSpecialNumber = num;
      activeSelectionTarget = 'main';
    }
    render();
    return;
  }

  if (selectedNumbers.includes(num)) {
    selectedNumbers = selectedNumbers.filter(n => n !== num);
  } else {
    if (selectedNumbers.length >= 6) {
      if (selectedCategory === 'POWER' && selectedSpecialNumber === null) {
        statusMessage = {
          type: 'warning',
          text: `Đã chọn đủ 6 số chính! Bạn có thể chọn số ${num} làm Số phụ (Jackpot 2) hoặc chuyển sang ô Số phụ để chọn.`,
        };
        activeSelectionTarget = 'special';
        selectedSpecialNumber = num;
        render();
        return;
      }
      statusMessage = {
        type: 'danger',
        text: 'Bạn đã chọn đủ 6 số chính! Hãy bấm "Lưu bộ số" hoặc bỏ bớt số để chọn lại.',
      };
      render();
      return;
    }

    if (selectedCategory === 'POWER' && selectedSpecialNumber === num) {
      selectedSpecialNumber = null;
    }

    selectedNumbers.push(num);
    selectedNumbers.sort((a, b) => a - b);

    if (selectedCategory === 'POWER' && selectedNumbers.length === 6 && selectedSpecialNumber === null) {
      activeSelectionTarget = 'special';
      statusMessage = {
        type: 'warning',
        text: 'Đã chọn đủ 6 số chính! Tiếp tục bấm chọn 1 con số bất kỳ trong bảng làm ⭐ Số phụ (Jackpot 2).',
      };
    }
  }
  render();
}

function randomPick(): void {
  statusMessage = null;
  const max = getMaxLimit();
  const set = new Set<number>();
  while (set.size < 6) {
    set.add(Math.floor(Math.random() * max) + 1);
  }
  selectedNumbers = Array.from(set).sort((a, b) => a - b);

  if (selectedCategory === 'POWER') {
    let sp: number;
    do {
      sp = Math.floor(Math.random() * 55) + 1;
    } while (set.has(sp));
    selectedSpecialNumber = sp;
  } else {
    selectedSpecialNumber = null;
  }
  activeSelectionTarget = 'main';
  render();
}

function setDate(newDate: string): void {
  selectedDate = newDate;
  const autoCat = getCategoryFromDate(newDate);
  selectedCategory = autoCat;
  if (autoCat === 'MEGA') {
    selectedNumbers = selectedNumbers.filter(n => n <= 45);
    selectedSpecialNumber = null;
  }
  render();
}

function switchCategory(cat: 'MEGA' | 'POWER'): void {
  selectedCategory = cat;
  if (cat === 'MEGA') {
    selectedNumbers = selectedNumbers.filter(n => n <= 45);
    selectedSpecialNumber = null;
    activeSelectionTarget = 'main';
  }
  render();
}

// Render DOM
function render(): void {
  const root = document.getElementById('app');
  if (!root) return;

  const maxLimit = getMaxLimit();
  const gridNumbers = Array.from({ length: maxLimit }, (_, i) => i + 1);
  const dayName = getDayOfWeekName(selectedDate);

  root.innerHTML = `
    <div class="min-vh-100 bg-light d-flex flex-column">
      <!-- 1. Header Zone -->
      <header id="app-header" class="navbar navbar-dark bg-dark px-3 px-md-4 py-2 border-bottom shadow-sm flex-shrink-0">
        <div class="container-fluid d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center gap-2">
            <span class="fs-4">🎰</span>
            <div>
              <h1 class="h5 mb-0 text-white fw-bold">Analyze Project</h1>
              <small class="text-secondary d-none d-sm-inline">
                Spring Framework &bull; Angular Structure &bull; XGBoost AI &bull; Power 6/55 Số Phụ
              </small>
            </div>
          </div>
        </div>
      </header>

      <!-- 2. Main Area: Left Menu + Content (3-Zone Layout) -->
      <div class="d-flex flex-column flex-md-row flex-grow-1">
        <!-- Zone 2: Left Menu -->
        <aside id="app-left-menu" class="bg-white border-end p-3 flex-shrink-0" style="min-width: 250px;">
          <div class="mb-3 d-none d-md-block">
            <small class="text-uppercase text-muted fw-bold">Chức năng hệ thống</small>
          </div>

          <div class="nav nav-pills flex-row flex-md-column gap-2 mb-3">
            <button
              id="menu-item-prediction"
              type="button"
              class="nav-link text-start d-flex align-items-center gap-2 flex-grow-1 flex-md-grow-0 ${activeTab === 'prediction' ? 'active shadow-sm' : 'text-dark'}"
            >
              <span>⚡</span>
              <div>
                <div class="fw-semibold">Dự đoán AI XGBoost</div>
                <small class="d-none d-md-block text-muted opacity-75">6 Số đầu trang & Lịch sử</small>
              </div>
            </button>

            <button
              id="menu-item-manual"
              type="button"
              class="nav-link text-start d-flex align-items-center gap-2 flex-grow-1 flex-md-grow-0 ${activeTab === 'manual' ? 'active shadow-sm' : 'text-dark'}"
            >
              <span>📝</span>
              <div>
                <div class="fw-semibold">Nhập số theo ngày</div>
                <small class="d-none d-md-block text-muted opacity-75">Mega & Power (+Số phụ)</small>
              </div>
            </button>
          </div>

          <!-- Schedule box -->
          <div class="mt-4 pt-3 border-top d-none d-md-block">
            <small class="text-uppercase text-muted fw-bold">Thể lệ quay thưởng</small>
            <div class="mt-2 small p-2 bg-light rounded border">
              <div class="d-flex align-items-center justify-content-between mb-1">
                <span class="fw-bold text-primary">🔵 POWER 6/55</span>
                <span class="badge bg-primary text-white">6 Số + 1 Số phụ</span>
              </div>
              <div class="text-muted" style="font-size: 0.8rem;">
                Quay: <strong>Thứ 3 &bull; Thứ 5 &bull; Thứ 7</strong><br />
                Jackpot 1: Trúng 6/6 số chính<br />
                Jackpot 2: Trúng 5/6 + Số phụ
              </div>
            </div>

            <div class="mt-2 small p-2 bg-light rounded border">
              <div class="d-flex align-items-center justify-content-between mb-1">
                <span class="fw-bold text-danger">🔴 MEGA 6/45</span>
                <span class="badge bg-danger text-white">6 Số (1 - 45)</span>
              </div>
              <div class="text-muted" style="font-size: 0.8rem;">
                Quay: <strong>Thứ 4 &bull; Thứ 6 &bull; Chủ nhật</strong>
              </div>
            </div>
          </div>
        </aside>

        <!-- Zone 3: Main Content -->
        <main id="app-content" class="flex-grow-1 p-3 p-md-4 overflow-auto">
          ${activeTab === 'prediction' ? renderPredictionView() : renderManualView(maxLimit, gridNumbers, dayName)}
        </main>
      </div>
    </div>
  `;

  attachEventListeners();
}

function renderManualView(maxLimit: number, gridNumbers: number[], dayName: string): string {
  const isPower = selectedCategory === 'POWER';

  return `
    <div id="manual-entry-feature" class="mx-auto" style="max-width: 940px;">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-body p-3 p-md-4">
          <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
            <div>
              <h2 class="h4 fw-bold mb-1 text-primary">
                ${isPower ? 'Nhập dãy số Power 6/55 (6 Số chính + Số phụ)' : 'Nhập dãy 6 số Mega 6/45'}
              </h2>
              <p class="text-muted mb-0 small">
                ${isPower
                  ? '⚡ Thể lệ Power 6/55: Nhập 6 số chính và <strong>1 số phụ (Jackpot 2)</strong>. Số phụ được thuật toán dùng để tính bảo hiểm Jackpot 2 khi sai 1 số.'
                  : 'Tự động nhận diện danh mục: <strong>Mega (1-45)</strong> hoặc <strong>Power (1-55)</strong> theo thứ trong tuần.'}
              </p>
            </div>
          </div>

          <!-- Category Selection -->
          <div class="p-3 mb-4 rounded-3 border bg-light shadow-sm">
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
              <label class="fw-bold text-dark mb-0">🏷️ Chọn danh mục xổ số:</label>
              <span class="small text-muted">Tự động chuyển theo thứ trong tuần của ngày quay</span>
            </div>

            <div class="row g-2">
              <div class="col-md-6">
                <button
                  type="button"
                  id="btn-cat-power"
                  class="btn w-100 p-3 text-start border rounded-3 transition ${isPower ? 'btn-primary text-white shadow-sm' : 'btn-white bg-white text-dark'}"
                >
                  <div class="d-flex justify-content-between align-items-center">
                    <span class="fw-bold fs-6">🔵 POWER 6/55 (Có Số Phụ)</span>
                    <span class="badge ${isPower ? 'bg-warning text-dark' : 'bg-primary-subtle text-primary'}">
                      6 số + 1 số phụ
                    </span>
                  </div>
                  <small class="d-block mt-1 opacity-75">
                    📅 Lịch quay: <strong>Thứ 3 &bull; Thứ 5 &bull; Thứ 7</strong> &bull; Dải số 01 &rarr; 55
                  </small>
                </button>
              </div>

              <div class="col-md-6">
                <button
                  type="button"
                  id="btn-cat-mega"
                  class="btn w-100 p-3 text-start border rounded-3 transition ${!isPower ? 'btn-danger text-white shadow-sm' : 'btn-white bg-white text-dark'}"
                >
                  <div class="d-flex justify-content-between align-items-center">
                    <span class="fw-bold fs-6">🔴 MEGA 6/45</span>
                    <span class="badge ${!isPower ? 'bg-white text-danger' : 'bg-danger-subtle text-danger'}">
                      Số: 01 &rarr; 45
                    </span>
                  </div>
                  <small class="d-block mt-1 opacity-75">
                    📅 Lịch quay: <strong>Thứ 4 &bull; Thứ 6 &bull; Chủ nhật</strong>
                  </small>
                </button>
              </div>
            </div>
          </div>

          <!-- Date Selector -->
          <div class="p-3 mb-4 rounded-3 border bg-white shadow-sm">
            <div class="row align-items-center g-3">
              <div class="col-md-5">
                <label class="form-label fw-bold text-dark mb-1">📅 Ngày mở thưởng:</label>
                <input
                  id="date-picker-input"
                  type="date"
                  class="form-control form-control-lg fw-bold text-primary"
                  value="${selectedDate}"
                />
              </div>
              <div class="col-md-7">
                <label class="form-label text-muted small mb-1">Chọn nhanh & Thông tin:</label>
                <div class="d-flex gap-2 flex-wrap align-items-center">
                  <button type="button" id="btn-date-today" class="btn btn-outline-primary btn-sm px-3 ${selectedDate === getTodayDateString() ? 'active' : ''}">
                    🌟 Hôm nay
                  </button>
                  <button type="button" id="btn-date-yesterday" class="btn btn-outline-secondary btn-sm px-3 ${selectedDate === getYesterdayDateString() ? 'active' : ''}">
                    ⏪ Hôm qua
                  </button>
                  <span class="badge bg-light text-dark border px-3 py-2 ms-auto">
                    ${dayName} &bull; <strong>${isPower ? 'Power 6/55' : 'Mega 6/45'}</strong>
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Selected Numbers Preview (6 Main Numbers + Special Number for Power) -->
          <div class="p-3 bg-light rounded-3 mb-4">
            <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
              <div>
                <span class="fw-bold text-dark">
                  Dãy số đã chọn cho ngày <strong>${selectedDate}</strong> (${selectedCategory}):
                </span>
              </div>

              ${isPower ? `
                <div class="btn-group btn-group-sm shadow-sm" role="group">
                  <button
                    type="button"
                    id="btn-target-main"
                    class="btn ${activeSelectionTarget === 'main' ? 'btn-primary' : 'btn-outline-primary bg-white'}"
                  >
                    🔘 6 Số chính (${selectedNumbers.length}/6)
                  </button>
                  <button
                    type="button"
                    id="btn-target-special"
                    class="btn ${activeSelectionTarget === 'special' ? 'btn-warning text-dark fw-bold' : 'btn-outline-warning bg-white text-dark'}"
                  >
                    ⭐ Số phụ Jackpot 2 (${selectedSpecialNumber !== null ? '1/1' : '0/1'})
                  </button>
                </div>
              ` : `
                <span class="badge ${selectedNumbers.length === 6 ? 'bg-success' : 'bg-primary'} px-3 py-2">
                  ${selectedNumbers.length} / 6 số
                </span>
              `}
            </div>

            <!-- Numbers Display Row -->
            <div class="d-flex justify-content-center align-items-center gap-2 gap-md-3 flex-wrap my-2">
              <!-- 6 Main Numbers -->
              <div class="d-flex gap-2 gap-md-3">
                ${[0, 1, 2, 3, 4, 5].map(idx => {
                  const val = selectedNumbers[idx];
                  return `
                    <div class="text-center" style="width: 58px;">
                      <input
                        id="slot-input-${idx}"
                        type="number"
                        min="1"
                        max="${maxLimit}"
                        class="form-control text-center fw-bold slot-main-input"
                        data-slot-idx="${idx}"
                        value="${val !== undefined ? val : ''}"
                        placeholder="--"
                        style="width: 52px; height: 52px; border-radius: 50%; font-size: 1.25rem; border: 2px solid ${activeSelectionTarget === 'main' ? '#0d6efd' : '#6c757d'}; margin: 0 auto 4px auto; background-color: #fff;"
                      />
                      <small class="text-muted" style="font-size: 0.75rem;">Số ${idx + 1}</small>
                    </div>
                  `;
                }).join('')}
              </div>

              <!-- Separator & Special Number for POWER -->
              ${isPower ? `
                <div class="d-flex align-items-center px-1">
                  <span class="fs-4 text-muted fw-bold">+</span>
                </div>

                <div class="text-center p-2 rounded-3 border ${activeSelectionTarget === 'special' ? 'border-warning bg-warning-subtle shadow-sm' : 'border-secondary-subtle bg-white'}" style="min-width: 90px; cursor: pointer;" id="slot-special-container">
                  <div class="d-flex flex-column align-items-center">
                    <input
                      id="slot-special-input"
                      type="number"
                      min="1"
                      max="55"
                      class="form-control text-center fw-bold border-2 border-warning shadow-sm"
                      value="${selectedSpecialNumber !== null ? selectedSpecialNumber : ''}"
                      placeholder="⭐"
                      style="width: 54px; height: 54px; border-radius: 50%; font-size: 1.3rem; background: radial-gradient(circle at 18px 18px, #fff3cd, #ffc107); color: #000; margin: 0 auto 4px auto;"
                    />
                    <span class="badge bg-warning text-dark fw-bold" style="font-size: 0.72rem;">
                      ⭐ Số phụ
                    </span>
                    <small class="text-muted mt-1" style="font-size: 0.7rem;">Jackpot 2</small>
                  </div>
                </div>
              ` : ''}
            </div>

            ${isPower ? `
              <div class="mt-3 text-center small">
                ${activeSelectionTarget === 'main'
                  ? `<span class="badge bg-primary-subtle text-primary border border-primary-subtle px-3 py-1">👉 Đang chọn <strong>6 số chính</strong> trong bảng bên dưới (${selectedNumbers.length}/6). Bấm vào ô "Số phụ" để đổi sang chọn số phụ.</span>`
                  : `<span class="badge bg-warning-subtle text-dark border border-warning px-3 py-1">👉 Đang chọn <strong>Số phụ (Jackpot 2)</strong> trong bảng bên dưới. Chọn 1 số từ 01-55 (không trùng với 6 số chính).</span>`}
              </div>
            ` : ''}
          </div>

          <!-- Number Grid -->
          <div class="mb-4">
            <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
              <span class="fw-semibold text-dark">
                Bảng số chọn nhanh (01 &rarr; ${maxLimit}):
              </span>
              <div class="d-flex gap-2">
                <button type="button" id="btn-quick-random" class="btn btn-sm btn-outline-primary">
                  🎲 Ngẫu nhiên ${isPower ? '6 số + 1 số phụ' : '6 số'}
                </button>
                <button type="button" id="btn-quick-reset" class="btn btn-sm btn-outline-secondary">🧹 Xóa chọn lại</button>
              </div>
            </div>

            <div class="d-grid gap-2 p-2 bg-white rounded border" style="grid-template-columns: repeat(auto-fill, minmax(42px, 1fr));">
              ${gridNumbers.map(n => {
                const isMainSelected = selectedNumbers.includes(n);
                const isSpecialSelected = isPower && selectedSpecialNumber === n;

                let ballClass = 'btn-outline-light text-dark';
                let style = 'height: 42px; border: 1px solid #dee2e6;';

                if (isMainSelected) {
                  ballClass = 'btn-primary text-white shadow-sm';
                  style = 'height: 42px; border: none;';
                } else if (isSpecialSelected) {
                  ballClass = 'btn-warning text-dark fw-bold shadow-sm';
                  style = 'height: 42px; border: 2px solid #ff9800; background: radial-gradient(circle at 14px 14px, #fff3cd, #ffc107);';
                }

                return `
                  <button
                    type="button"
                    class="btn p-0 fw-bold rounded-circle btn-grid-ball ${ballClass}"
                    data-num="${n}"
                    style="${style}"
                    title="${isMainSelected ? `Số chính #${n}` : isSpecialSelected ? `Số phụ Jackpot 2 #${n}` : `Chọn số ${n}`}"
                  >
                    ${isSpecialSelected ? '★' : ''}${n < 10 ? '0' + n : n}
                  </button>
                `;
              }).join('')}
            </div>
          </div>

          <!-- Note & Save -->
          <div class="row g-3 mb-3">
            <div class="col-md-8">
              <input
                id="input-record-note"
                type="text"
                class="form-control"
                placeholder="Ghi chú (Ví dụ: Vé Power 6/55 kỳ quay ${selectedDate}...)"
                value="${noteText}"
              />
            </div>
            <div class="col-md-4">
              <button
                id="btn-save-to-h2"
                type="button"
                class="btn btn-primary w-100 fw-bold py-2 shadow-sm"
                ${isSaving || selectedNumbers.length !== 6 ? 'disabled' : ''}
              >
                ${isSaving ? 'Đang lưu...' : '💾 Lưu bộ số vào hệ thống'}
              </button>
            </div>
          </div>

          ${statusMessage ? `
            <div class="alert alert-${statusMessage.type} py-2 mt-3 mb-0" role="alert">
              ${statusMessage.text}
            </div>
          ` : ''}
        </div>
      </div>

      <!-- Saved Records Table -->
      <div class="card shadow-sm border-0">
        <div class="card-header bg-white py-3">
          <div class="d-flex justify-content-between align-items-center flex-wrap gap-3">
            <div>
              <h3 class="h5 mb-0 fw-bold text-dark">
                Các bộ số đã lưu (${savedRecords.length})
              </h3>
              <small class="text-muted">Phân loại Mega 6/45 & Power 6/55 (kèm Số phụ Jackpot 2)</small>
            </div>

            <!-- Filters -->
            <div class="d-flex align-items-center gap-2 flex-wrap">
              <select id="select-filter-category" class="form-select form-select-sm" style="width: 145px;">
                <option value="" ${filterCategory === '' ? 'selected' : ''}>Tất cả danh mục</option>
                <option value="POWER" ${filterCategory === 'POWER' ? 'selected' : ''}>🔵 Power 6/55</option>
                <option value="MEGA" ${filterCategory === 'MEGA' ? 'selected' : ''}>🔴 Mega 6/45</option>
              </select>

              <input
                id="input-filter-date"
                type="date"
                class="form-control form-control-sm"
                style="width: 145px;"
                value="${filterDate}"
              />

              ${(filterDate || filterCategory) ? `
                <button type="button" id="btn-clear-filters" class="btn btn-sm btn-outline-secondary text-nowrap">
                  Hiện tất cả
                </button>
              ` : ''}

              <button type="button" id="btn-refresh-list" class="btn btn-sm btn-outline-primary text-nowrap">
                🔄 Làm mới
              </button>
            </div>
          </div>
        </div>

        <div class="card-body p-0">
          ${savedRecords.length === 0 ? `
            <div class="text-center py-5 text-muted">
              <p class="mb-0">Chưa có bản ghi nào phù hợp trong hệ thống.</p>
              <small>Hãy chọn danh mục (Mega/Power), chọn các số và bấm "Lưu bộ số".</small>
            </div>
          ` : `
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0">
                <thead class="table-light">
                  <tr>
                    <th scope="col" style="width: 50px;">ID</th>
                    <th scope="col" style="width: 125px;">Danh mục</th>
                    <th scope="col" style="width: 135px;">Ngày mở thưởng</th>
                    <th scope="col">Dãy số & Số phụ</th>
                    <th scope="col">Ghi chú</th>
                    <th scope="col" class="text-end" style="width: 80px;">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  ${savedRecords.map(r => `
                    <tr>
                      <td class="fw-bold text-muted">#${r.id}</td>
                      <td>
                        <span class="badge px-2 py-1 ${r.category === 'POWER' ? 'bg-primary-subtle text-primary border border-primary-subtle' : 'bg-danger-subtle text-danger border border-danger-subtle'}">
                          ${r.category === 'POWER' ? '🔵 Power 6/55' : '🔴 Mega 6/45'}
                        </span>
                      </td>
                      <td>
                        <span class="badge bg-light text-dark border px-2 py-1">
                          📅 ${r.drawDate}
                        </span>
                      </td>
                      <td>
                        <div class="d-flex align-items-center gap-1 gap-md-2 flex-wrap">
                          ${r.numbers.map(n => `
                            <span
                              class="badge rounded-circle bg-primary text-white d-inline-flex align-items-center justify-content-center"
                              style="width: 32px; height: 32px; font-size: 0.85rem;"
                            >
                              ${n < 10 ? '0' + n : n}
                            </span>
                          `).join('')}

                          ${r.specialNumber !== undefined && r.specialNumber !== null ? `
                            <span class="text-muted fw-bold mx-1">+</span>
                            <span
                              class="badge rounded-circle bg-warning text-dark border border-warning shadow-sm d-inline-flex align-items-center justify-content-center fw-bold"
                              style="width: 34px; height: 34px; font-size: 0.85rem;"
                              title="Số phụ Jackpot 2: ${r.specialNumber}"
                            >
                              ★${r.specialNumber < 10 ? '0' + r.specialNumber : r.specialNumber}
                            </span>
                            <span class="badge bg-warning-subtle text-dark border border-warning-subtle small ms-1" style="font-size: 0.72rem;">
                              Số phụ: ${r.specialNumber < 10 ? '0' + r.specialNumber : r.specialNumber}
                            </span>
                          ` : ''}
                        </div>
                      </td>
                      <td>
                        ${r.note ? `<span class="badge bg-light text-dark border">${r.note}</span>` : '<span class="text-muted small fst-italic">--</span>'}
                      </td>
                      <td class="text-end">
                        <button type="button" class="btn btn-sm btn-outline-danger btn-delete-record" data-id="${r.id}">
                          Xóa
                        </button>
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          `}
        </div>
      </div>
    </div>
  `;
}

function renderPredictionView(): string {
  const isPower = predictionCategory === 'POWER';
  const catName = isPower ? 'Power 6/55' : 'Mega 6/45';
  const ballBgClass = isPower ? 'bg-primary text-white' : 'bg-danger text-white';

  // Extract candidate numbers for historical filtering
  const candidateNumbers: number[] = predictedNumbers.slice();
  if (isPower && predictedSpecialNumber !== null && !candidateNumbers.includes(predictedSpecialNumber)) {
    candidateNumbers.push(predictedSpecialNumber);
  }

  // Filter recent draws based on selected filter
  const allDraws = predictionResultData?.recentDraws || [];
  let filteredDraws = allDraws;
  if (historyFilterNumber !== null) {
    filteredDraws = allDraws.filter(d =>
      d.numbers.includes(historyFilterNumber!) || d.specialNumber === historyFilterNumber
    );
  }

  // Slicing for "Lúc đầu hiện 10 ngày thôi, bấm vào xem thêm thì hiện nhiều"
  const visibleDraws = filteredDraws.slice(0, historyLimit);

  return `
    <div id="prediction-feature" class="mx-auto" style="max-width: 1200px;">
      <!-- Category Switch & Action Controls Bar -->
      <div class="card shadow-sm border-0 mb-3">
        <div class="card-body p-3 p-md-4">
          <div class="d-flex justify-content-between align-items-center flex-wrap gap-3">
            <div>
              <div class="d-flex align-items-center gap-2 mb-1">
                <span class="fs-4">⚡</span>
                <h2 class="h4 fw-bold text-dark mb-0">Dự đoán AI XGBoost & Phân tích Chuỗi số</h2>
              </div>
              <p class="text-muted mb-0 small">
                Thuật toán XGBoost phân tích chuỗi thời gian, tần suất, chu kỳ hoàn vốn lô gan.
                ${isPower ? '<strong>Ở Power 6/55: Nếu sai 1 số trong 6 số chính thì tính thêm số phụ (Jackpot 2).</strong>' : 'Tối ưu 6 số chính cho Mega 6/45.'}
              </p>
            </div>

            <!-- Action buttons -->
            <div class="d-flex align-items-center gap-2 flex-wrap">
              <div class="btn-group shadow-sm" role="group">
                <button
                  type="button"
                  id="btn-pred-power"
                  class="btn fw-bold px-3 py-2 ${isPower ? 'btn-primary text-white' : 'btn-outline-primary bg-white'}"
                >
                  🔵 Power 6/55 (Có Số phụ)
                </button>
                <button
                  type="button"
                  id="btn-pred-mega"
                  class="btn fw-bold px-3 py-2 ${!isPower ? 'btn-danger text-white' : 'btn-outline-danger bg-white'}"
                >
                  🔴 Mega 6/45
                </button>
              </div>

              <button
                id="btn-predict-action"
                type="button"
                class="btn btn-warning fw-bold px-3 py-2 shadow-sm d-flex align-items-center gap-1"
                ${isPredicting ? 'disabled' : ''}
              >
                ${isPredicting ? `
                  <span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  Đang phân tích...
                ` : `🎯 Phân tích lại`}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 1. YÊU CẦU 1: CHO 6 SỐ HIỆN Ở ĐẦU TRANG -->
      ${predictionResultData ? `
        <div class="card shadow-sm border-0 mb-4" style="background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);">
          <div class="card-body p-4">
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
              <div>
                <span class="badge ${isPower ? 'bg-primary' : 'bg-danger'} text-white px-3 py-1 mb-1">
                  ĐỀ XUẤT TỐI ƯU &bull; ${catName.toUpperCase()}
                </span>
                <h3 class="h4 fw-bold text-dark mb-0">
                  Dãy số được AI đề xuất cho ${catName}
                </h3>
              </div>

              <div class="d-flex align-items-center gap-2">
                <span class="badge bg-light text-dark border px-3 py-2 small">
                  ⚖️ Tỷ lệ: <strong>${predictionResultData.oddEvenRatio || '3 Chẵn / 3 Lẻ'}</strong>
                </span>
                <button id="btn-save-predicted" type="button" class="btn btn-outline-primary fw-semibold btn-sm px-3 py-2 shadow-sm">
                  💾 Lưu bộ số này
                </button>
              </div>
            </div>

            <!-- Balls Display Container (Right at the top) -->
            <div class="p-3 p-md-4 rounded-3 border bg-white shadow-sm mb-3">
              <div class="d-flex justify-content-center align-items-center gap-3 gap-md-4 flex-wrap">
                <!-- 6 Main Numbers -->
                <div class="d-flex gap-3 gap-md-4 flex-wrap justify-content-center">
                  ${(predictionResultData.details || predictedNumbers.map((n: number) => ({ number: n, probabilityPercent: 75, tag: 'CÂN BẰNG' }))).map((detail: any) => `
                    <div class="d-flex flex-column align-items-center text-center" style="min-width: 68px;">
                      <div
                        class="d-flex align-items-center justify-content-center rounded-circle ${ballBgClass} fw-bold shadow transition-hover"
                        style="width: 60px; height: 60px; font-size: 1.5rem; letter-spacing: -0.5px;"
                      >
                        ${detail.number < 10 ? '0' + detail.number : detail.number}
                      </div>
                      <span class="badge bg-light text-dark border mt-2 px-2 py-1" style="font-size: 0.72rem;">
                        ${detail.tag || 'CÂN BẰNG'}
                      </span>
                      <small class="text-muted fw-semibold mt-1" style="font-size: 0.75rem;">
                        ${detail.probabilityPercent || 75}%
                      </small>
                    </div>
                  `).join('')}
                </div>

                <!-- Special Ball for POWER (Jackpot 2) -->
                ${isPower && predictionResultData.specialNumber !== undefined ? `
                  <div class="d-flex align-items-center px-1">
                    <span class="fs-1 text-muted fw-bold">+</span>
                  </div>

                  <div class="d-flex flex-column align-items-center text-center p-2 rounded-3 border border-warning bg-warning-subtle shadow-sm" style="min-width: 95px;">
                    <div
                      class="d-flex align-items-center justify-content-center rounded-circle fw-bold shadow"
                      style="width: 62px; height: 62px; font-size: 1.5rem; background: radial-gradient(circle at 20px 20px, #fff3cd, #ffc107); color: #000; border: 2px solid #ff9800;"
                    >
                      ★${predictionResultData.specialNumber < 10 ? '0' + predictionResultData.specialNumber : predictionResultData.specialNumber}
                    </div>
                    <span class="badge bg-warning text-dark border border-warning mt-2 fw-bold px-2 py-1" style="font-size: 0.72rem;">
                      ⭐ SỐ PHỤ
                    </span>
                    <small class="text-dark fw-bold mt-1" style="font-size: 0.75rem;">
                      ${predictionResultData.specialNumberDetail?.probabilityPercent || 78}%
                    </small>
                  </div>
                ` : ''}
              </div>

              ${isPower && predictionResultData.specialNumber !== undefined ? `
                <div class="mt-3 pt-3 border-top text-center small text-muted">
                  💡 <strong>Quy tắc bảo hiểm Jackpot 2:</strong> Nếu bạn sai 1 số bất kỳ trong 6 số chính (khớp 5/6 số), con <strong>Số phụ ⭐${predictionResultData.specialNumber < 10 ? '0' + predictionResultData.specialNumber : predictionResultData.specialNumber}</strong> sẽ là điều kiện bù trừ để trúng giải thưởng Jackpot 2.
                </div>
              ` : ''}
            </div>

            ${predictSaveSuccess ? `
              <div class="alert alert-success py-2 mb-0" role="alert">
                ${predictSaveSuccess}
              </div>
            ` : ''}
          </div>
        </div>

        <!-- 2. TWO-COLUMN LAYOUT: LÝ DO NÊN CHỌN BÊN TRÁI & LỊCH SỬ TỪNG SỐ BÊN PHẢI -->
        <div class="row g-4">
          <!-- CỘT TRÁI: MỤC LÍ DO NÊN CHỌN 6 SỐ BÊN DƯỚI -->
          <div class="col-lg-7">
            <div class="card shadow-sm border-0 mb-4 h-100">
              <div class="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
                <div class="d-flex align-items-center gap-2">
                  <span class="fs-5">💡</span>
                  <h4 class="h5 fw-bold text-dark mb-0">Lý do nên chọn bộ số này</h4>
                </div>
                <span class="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1 small">
                  Phân tích thuật toán XGBoost
                </span>
              </div>

              <div class="card-body p-3 p-md-4">
                <!-- Tóm tắt tổng thể -->
                <div class="p-3 mb-4 rounded-3 border bg-light">
                  <div class="fw-bold text-dark mb-1 small text-uppercase">
                    📌 Đánh giá chiến lược tổng thể:
                  </div>
                  <p class="mb-0 text-secondary small" style="line-height: 1.6;">
                    ${predictionResultData.overallReason || predictionResultData.analysisSummary || ''}
                  </p>
                </div>

                <!-- Danh sách lí do của từng con số -->
                <h5 class="h6 fw-bold text-dark mb-3 text-uppercase">
                  Chi tiết cơ sở chọn cho từng con số:
                </h5>

                <div class="d-flex flex-column gap-3">
                  ${(predictionResultData.selectionReasons || []).map(r => {
                    const isSpecial = r.role === 'special';
                    const ballBorder = isSpecial ? 'border-warning bg-warning-subtle' : 'border-primary-subtle bg-light';

                    let badgeColor = 'bg-primary-subtle text-primary border-primary-subtle';
                    if (r.tag === 'SỐ NÓNG') badgeColor = 'bg-danger-subtle text-danger border-danger-subtle';
                    if (r.tag === 'LÔ GAN') badgeColor = 'bg-info-subtle text-info border-info-subtle';
                    if (r.tag === 'CẶP ĐI KÈM') badgeColor = 'bg-success-subtle text-success border-success-subtle';
                    if (r.tag === 'BẢO HIỂM JACKPOT 2') badgeColor = 'bg-warning text-dark border-warning fw-bold';

                    return `
                      <div class="p-3 rounded-3 border ${ballBorder} shadow-sm transition">
                        <div class="d-flex align-items-start gap-3">
                          <!-- Ball Icon -->
                          <div
                            class="rounded-circle d-flex align-items-center justify-content-center fw-bold flex-shrink-0 ${isSpecial ? 'bg-warning text-dark border border-warning shadow-sm' : 'bg-primary text-white shadow-sm'}"
                            style="width: 44px; height: 44px; font-size: 1.15rem;"
                          >
                            ${isSpecial ? '★' : ''}${r.number < 10 ? '0' + r.number : r.number}
                          </div>

                          <!-- Reason content -->
                          <div class="flex-grow-1">
                            <div class="d-flex align-items-center justify-content-between flex-wrap gap-1 mb-1">
                              <span class="fw-bold text-dark">
                                ${isSpecial ? `Số Phụ ★${r.number < 10 ? '0' + r.number : r.number}` : `Số Chính ${r.number < 10 ? '0' + r.number : r.number}`} &bull; ${r.title}
                              </span>
                              <span class="badge ${badgeColor} border px-2 py-1" style="font-size: 0.72rem;">
                                ${r.tag}
                              </span>
                            </div>

                            <p class="text-secondary small mb-2" style="line-height: 1.5;">
                              ${r.reason}
                            </p>

                            <!-- Mini Metrics Pills -->
                            <div class="d-flex align-items-center gap-2 flex-wrap" style="font-size: 0.75rem;">
                              <span class="badge bg-white text-muted border">
                                📊 Tần suất: <strong>${r.frequency} lần</strong>
                              </span>
                              <span class="badge bg-white text-muted border">
                                ⏳ Độ trễ / Lô gan: <strong>${r.drawGap} kỳ</strong>
                              </span>
                              <span class="badge bg-white text-dark border">
                                🎯 Xác suất: <strong>${r.probabilityPercent}%</strong>
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    `;
                  }).join('')}
                </div>

                <!-- Extra Statistical Insights Grid -->
                <div class="row g-2 mt-3 pt-3 border-top">
                  <div class="col-sm-6">
                    <div class="p-2 border rounded bg-white">
                      <small class="fw-bold text-danger d-block mb-1">🔥 Top số nóng (Hay về):</small>
                      <div class="d-flex gap-1 flex-wrap">
                        ${(predictionResultData.hotNumbers || []).map(n => `
                          <span class="badge bg-danger-subtle text-danger border border-danger-subtle">${n < 10 ? '0' + n : n}</span>
                        `).join('')}
                      </div>
                    </div>
                  </div>

                  <div class="col-sm-6">
                    <div class="p-2 border rounded bg-white">
                      <small class="fw-bold text-primary d-block mb-1">❄️ Top lô gan (Lâu chưa ra):</small>
                      <div class="d-flex gap-1 flex-wrap">
                        ${(predictionResultData.coldNumbers || []).map(n => `
                          <span class="badge bg-primary-subtle text-primary border border-primary-subtle">${n < 10 ? '0' + n : n}</span>
                        `).join('')}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- CỘT PHẢI: YÊU CẦU 3 - DANH SÁCH LỊCH SỬ CỦA TỪNG SỐ (LÚC ĐẦU 10 NGÀY, XEM THÊM) -->
          <div class="col-lg-5">
            <div class="card shadow-sm border-0 mb-4 h-100">
              <div class="card-header bg-white py-3 border-bottom">
                <div class="d-flex justify-content-between align-items-center flex-wrap gap-2">
                  <div class="d-flex align-items-center gap-2">
                    <span class="fs-5">📅</span>
                    <div>
                      <h4 class="h5 fw-bold text-dark mb-0">Lịch sử của từng số</h4>
                      <small class="text-muted">Các ngày mở thưởng trong quá khứ</small>
                    </div>
                  </div>
                  <span class="badge bg-light text-dark border px-2 py-1">
                    Tổng: ${filteredDraws.length} ngày
                  </span>
                </div>
              </div>

              <div class="card-body p-3">
                <!-- Filter Pills: Chọn xem lịch sử của số cụ thể hoặc tất cả 6 số -->
                <div class="mb-3">
                  <small class="fw-bold text-dark d-block mb-2 text-uppercase" style="font-size: 0.75rem;">
                    Bấm để lọc lịch sử từng số:
                  </small>
                  <div class="d-flex gap-1 flex-wrap">
                    <button
                      type="button"
                      class="btn btn-sm btn-filter-num ${historyFilterNumber === null ? 'btn-dark text-white fw-bold' : 'btn-outline-secondary bg-white'}"
                      data-num="all"
                    >
                      Tất cả số
                    </button>

                    ${predictedNumbers.map(num => `
                      <button
                        type="button"
                        class="btn btn-sm btn-filter-num ${historyFilterNumber === num ? 'btn-primary text-white fw-bold shadow-sm' : 'btn-outline-primary bg-white'}"
                        data-num="${num}"
                      >
                        ${num < 10 ? '0' + num : num}
                      </button>
                    `).join('')}

                    ${isPower && predictedSpecialNumber !== null ? `
                      <button
                        type="button"
                        class="btn btn-sm btn-filter-num ${historyFilterNumber === predictedSpecialNumber ? 'btn-warning text-dark fw-bold shadow-sm' : 'btn-outline-warning bg-white text-dark'}"
                        data-num="${predictedSpecialNumber}"
                      >
                        ⭐ ${predictedSpecialNumber < 10 ? '0' + predictedSpecialNumber : predictedSpecialNumber}
                      </button>
                    ` : ''}
                  </div>

                  <!-- Active Filter Notice -->
                  <div class="mt-2 text-muted small">
                    ${historyFilterNumber === null
                      ? '👉 Đang xem toàn bộ các ngày mở thưởng (các số trùng khớp với bộ đề xuất được tô đậm màu xanh/vàng).'
                      : `👉 Đang lọc riêng lịch sử các ngày mở thưởng có xuất hiện con <strong>Số ${historyFilterNumber < 10 ? '0' + historyFilterNumber : historyFilterNumber}</strong> (${filteredDraws.length} ngày).`}
                  </div>
                </div>

                <!-- Danh sách các ngày mở thưởng -->
                ${visibleDraws.length === 0 ? `
                  <div class="text-center py-4 text-muted bg-light rounded border">
                    <p class="mb-0 small">Không tìm thấy ngày mở thưởng nào cho con số này.</p>
                  </div>
                ` : `
                  <div class="d-flex flex-column gap-2 mb-3">
                    ${visibleDraws.map(draw => {
                      // Check which numbers of the recommended set match this draw
                      const matchedMains = draw.numbers.filter(n => predictedNumbers.includes(n));
                      const isSpecialMatched = isPower && predictedSpecialNumber !== null && draw.specialNumber === predictedSpecialNumber;
                      const hasFilteredTarget = historyFilterNumber !== null && (draw.numbers.includes(historyFilterNumber) || draw.specialNumber === historyFilterNumber);

                      let cardBorder = 'border';
                      let cardBg = 'bg-white';
                      if (hasFilteredTarget) {
                        cardBorder = 'border-primary border-2';
                        cardBg = 'bg-primary-subtle';
                      }

                      return `
                        <div class="p-2 p-md-3 rounded border ${cardBorder} ${cardBg} shadow-xs">
                          <div class="d-flex justify-content-between align-items-center mb-1 flex-wrap gap-1">
                            <div class="d-flex align-items-center gap-1">
                              <span class="badge bg-light text-dark border fw-bold" style="font-size: 0.75rem;">
                                📅 ${draw.drawDate}
                              </span>
                              <span class="text-muted small" style="font-size: 0.75rem;">
                                (${getDayOfWeekName(draw.drawDate)})
                              </span>
                            </div>

                            <!-- Match indicator badge -->
                            <div>
                              ${matchedMains.length > 0 || isSpecialMatched ? `
                                <span class="badge bg-success-subtle text-success border border-success-subtle" style="font-size: 0.7rem;">
                                  Trùng ${matchedMains.length} số${isSpecialMatched ? ' + Số phụ ⭐' : ''}
                                </span>
                              ` : `
                                <span class="badge bg-light text-muted border" style="font-size: 0.7rem;">
                                  Kỳ quay lịch sử
                                </span>
                              `}
                            </div>
                          </div>

                          <!-- Balls row -->
                          <div class="d-flex align-items-center gap-1 flex-wrap mt-2">
                            ${draw.numbers.map(n => {
                              const isHit = predictedNumbers.includes(n);
                              const isTargetSelected = historyFilterNumber === n;

                              let ballClass = 'bg-light text-dark border';
                              let size = 'width: 28px; height: 28px; font-size: 0.78rem;';

                              if (isTargetSelected) {
                                ballClass = 'bg-primary text-white fw-bold shadow-sm';
                                size = 'width: 30px; height: 30px; font-size: 0.85rem;';
                              } else if (isHit) {
                                ballClass = 'bg-success text-white fw-bold';
                                size = 'width: 28px; height: 28px; font-size: 0.8rem;';
                              }

                              return `
                                <span
                                  class="rounded-circle d-inline-flex align-items-center justify-content-center ${ballClass}"
                                  style="${size}"
                                  title="${isHit ? `Trùng khớp với số đề xuất: #${n}` : `Số #${n}`}"
                                >
                                  ${n < 10 ? '0' + n : n}
                                </span>
                              `;
                            }).join('')}

                            <!-- Special number if present -->
                            ${draw.specialNumber !== undefined && draw.specialNumber !== null ? `
                              <span class="text-muted fw-bold small px-1">+</span>
                              <span
                                class="rounded-circle d-inline-flex align-items-center justify-content-center ${predictedSpecialNumber === draw.specialNumber ? 'bg-warning text-dark border border-warning fw-bold shadow-sm' : 'bg-warning-subtle text-dark border border-warning'}"
                                style="width: 30px; height: 30px; font-size: 0.8rem;"
                                title="Số phụ: ${draw.specialNumber}"
                              >
                                ★${draw.specialNumber < 10 ? '0' + draw.specialNumber : draw.specialNumber}
                              </span>
                            ` : ''}
                          </div>
                        </div>
                      `;
                    }).join('')}
                  </div>
                `}

                <!-- YÊU CẦU 3: LÚC ĐẦU HIỆN 10 NGÀY THÔI, BẤM VÀO XEM THÊM THÌ HIỆN NHIỀU -->
                <div class="p-3 bg-light rounded border text-center">
                  <div class="text-muted small mb-2">
                    Đang hiển thị <strong>${visibleDraws.length}</strong> / <strong>${filteredDraws.length}</strong> ngày mở thưởng
                  </div>

                  <div class="d-flex justify-content-center gap-2 flex-wrap">
                    ${historyLimit < filteredDraws.length ? `
                      <button
                        type="button"
                        id="btn-history-load-more"
                        class="btn btn-primary btn-sm px-3 fw-bold shadow-sm"
                      >
                        ⬇️ Xem thêm (+10 ngày)
                      </button>

                      <button
                        type="button"
                        id="btn-history-show-all"
                        class="btn btn-outline-secondary btn-sm px-2"
                      >
                        Xem toàn bộ (${filteredDraws.length})
                      </button>
                    ` : ''}

                    ${historyLimit > 10 ? `
                      <button
                        type="button"
                        id="btn-history-collapse"
                        class="btn btn-outline-secondary btn-sm px-3"
                      >
                        ⬆️ Thu gọn về 10 ngày
                      </button>
                    ` : ''}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      ` : `
        <div class="card shadow-sm border-0 text-center p-5">
          <div class="spinner-border text-primary mx-auto mb-3" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <h5 class="fw-bold text-dark">Đang khởi chạy thuật toán phân tích XGBoost...</h5>
          <p class="text-muted small mb-0">Vui lòng chờ trong giây lát.</p>
        </div>
      `}
    </div>
  `;
}

function attachEventListeners(): void {
  // Tab switching
  document.getElementById('menu-item-manual')?.addEventListener('click', () => {
    activeTab = 'manual';
    render();
  });
  document.getElementById('menu-item-prediction')?.addEventListener('click', () => {
    activeTab = 'prediction';
    render();
    if (!predictionResultData || predictionResultData.category !== predictionCategory) {
      runPrediction();
    }
  });

  // Manual Entry event listeners
  if (activeTab === 'manual') {
    document.getElementById('btn-cat-power')?.addEventListener('click', () => switchCategory('POWER'));
    document.getElementById('btn-cat-mega')?.addEventListener('click', () => switchCategory('MEGA'));

    document.getElementById('btn-target-main')?.addEventListener('click', () => {
      activeSelectionTarget = 'main';
      render();
    });

    document.getElementById('btn-target-special')?.addEventListener('click', () => {
      activeSelectionTarget = 'special';
      render();
    });

    document.getElementById('slot-special-container')?.addEventListener('click', () => {
      activeSelectionTarget = 'special';
      render();
    });

    const dateInput = document.getElementById('date-picker-input') as HTMLInputElement | null;
    dateInput?.addEventListener('change', (e) => {
      const val = (e.target as HTMLInputElement).value;
      if (val) setDate(val);
    });

    document.getElementById('btn-date-today')?.addEventListener('click', () => setDate(getTodayDateString()));
    document.getElementById('btn-date-yesterday')?.addEventListener('click', () => setDate(getYesterdayDateString()));

    document.getElementById('btn-quick-random')?.addEventListener('click', randomPick);
    document.getElementById('btn-quick-reset')?.addEventListener('click', () => {
      selectedNumbers = [];
      selectedSpecialNumber = null;
      activeSelectionTarget = 'main';
      noteText = '';
      statusMessage = null;
      render();
    });

    // Special input change
    const specInput = document.getElementById('slot-special-input') as HTMLInputElement | null;
    specInput?.addEventListener('change', (e) => {
      const val = parseInt((e.target as HTMLInputElement).value, 10);
      if (isNaN(val)) {
        selectedSpecialNumber = null;
      } else if (val >= 1 && val <= 55) {
        if (selectedNumbers.includes(val)) {
          statusMessage = { type: 'danger', text: `Số phụ (${val}) không được trùng với 6 số chính!` };
          (e.target as HTMLInputElement).value = '';
          selectedSpecialNumber = null;
        } else {
          selectedSpecialNumber = val;
          statusMessage = null;
        }
      }
      render();
    });

    // Main slots input change
    document.querySelectorAll('.slot-main-input').forEach(input => {
      input.addEventListener('change', (e) => {
        const idx = parseInt((e.currentTarget as HTMLElement).getAttribute('data-slot-idx') || '0', 10);
        const val = parseInt((e.target as HTMLInputElement).value, 10);
        const max = getMaxLimit();

        if (isNaN(val) || val < 1 || val > max) {
          selectedNumbers.splice(idx, 1);
        } else {
          if (selectedSpecialNumber === val) {
            selectedSpecialNumber = null;
          }
          selectedNumbers[idx] = val;
          selectedNumbers = Array.from(new Set(selectedNumbers.filter(n => n >= 1 && n <= max))).sort((a, b) => a - b);
        }
        render();
      });
    });

    document.querySelectorAll('.btn-grid-ball').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const num = parseInt((e.currentTarget as HTMLElement).getAttribute('data-num') || '', 10);
        if (!isNaN(num)) toggleNumber(num);
      });
    });

    const noteInput = document.getElementById('input-record-note') as HTMLInputElement | null;
    noteInput?.addEventListener('input', (e) => {
      noteText = (e.target as HTMLInputElement).value;
    });

    document.getElementById('btn-save-to-h2')?.addEventListener('click', saveToH2);

    // Filters
    const catFilter = document.getElementById('select-filter-category') as HTMLSelectElement | null;
    catFilter?.addEventListener('change', (e) => {
      filterCategory = (e.target as HTMLSelectElement).value;
      fetchSavedRecords();
    });

    const dateFilter = document.getElementById('input-filter-date') as HTMLInputElement | null;
    dateFilter?.addEventListener('change', (e) => {
      filterDate = (e.target as HTMLInputElement).value;
      fetchSavedRecords();
    });

    document.getElementById('btn-clear-filters')?.addEventListener('click', () => {
      filterDate = '';
      filterCategory = '';
      fetchSavedRecords();
    });

    document.getElementById('btn-refresh-list')?.addEventListener('click', () => fetchSavedRecords());

    document.querySelectorAll('.btn-delete-record').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = parseInt((e.currentTarget as HTMLElement).getAttribute('data-id') || '', 10);
        if (!isNaN(id)) deleteRecord(id);
      });
    });
  }

  // Prediction event listeners
  if (activeTab === 'prediction') {
    document.getElementById('btn-pred-power')?.addEventListener('click', () => {
      if (predictionCategory === 'POWER' && predictedNumbers.length > 0) return;
      predictionCategory = 'POWER';
      historyLimit = 10;
      historyFilterNumber = null;
      runPrediction();
    });

    document.getElementById('btn-pred-mega')?.addEventListener('click', () => {
      if (predictionCategory === 'MEGA' && predictedNumbers.length > 0) return;
      predictionCategory = 'MEGA';
      historyLimit = 10;
      historyFilterNumber = null;
      runPrediction();
    });

    document.getElementById('btn-predict-action')?.addEventListener('click', () => {
      historyLimit = 10;
      runPrediction();
    });

    document.getElementById('btn-save-predicted')?.addEventListener('click', savePredictedToH2);

    // Number filter chips for History View
    document.querySelectorAll('.btn-filter-num').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const numStr = (e.currentTarget as HTMLElement).getAttribute('data-num');
        if (numStr === 'all') {
          historyFilterNumber = null;
        } else {
          const parsed = parseInt(numStr || '', 10);
          historyFilterNumber = isNaN(parsed) ? null : parsed;
        }
        historyLimit = 10; // Reset to 10 when switching filter
        render();
      });
    });

    // "Xem thêm" (+10 ngày)
    document.getElementById('btn-history-load-more')?.addEventListener('click', () => {
      historyLimit += 10;
      render();
    });

    // "Xem toàn bộ"
    document.getElementById('btn-history-show-all')?.addEventListener('click', () => {
      historyLimit = 999;
      render();
    });

    // "Thu gọn về 10 ngày"
    document.getElementById('btn-history-collapse')?.addEventListener('click', () => {
      historyLimit = 10;
      render();
    });
  }
}

// Initial bootstrap
fetchSavedRecords();
runPrediction();
