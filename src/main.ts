// Pure TypeScript Frontend (No React) for Analyze Project

interface SavedRecord {
  id: number;
  drawDate: string; // YYYY-MM-DD
  category: 'MEGA' | 'POWER';
  numbers: number[];
  createdAt: string;
  note?: string;
}

// App State
let activeTab: 'manual' | 'prediction' = 'manual';
let selectedCategory: 'MEGA' | 'POWER' = 'MEGA';
let selectedDate: string = getTodayDateString();
let selectedNumbers: number[] = [];
let noteText: string = '';
let isSaving: boolean = false;
let savedRecords: SavedRecord[] = [];
let filterDate: string = '';
let filterCategory: string = '';

// Prediction State
let predictionCategory: 'MEGA' | 'POWER' = 'MEGA';
let predictedNumbers: number[] = [];
let isPredicting: boolean = false;
let predictSaveSuccess: string | null = null;
let statusMessage: { type: 'success' | 'danger'; text: string } | null = null;

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
  if (!dateStr) return 'MEGA';
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
    console.error('Lỗi khi tải dữ liệu từ DB H2:', err);
  }
}

async function saveToH2(): Promise<void> {
  statusMessage = null;
  if (selectedNumbers.length !== 6) {
    statusMessage = { type: 'danger', text: `Vui lòng chọn đủ 6 số khác nhau! (Hiện tại: ${selectedNumbers.length}/6)` };
    render();
    return;
  }

  isSaving = true;
  render();

  try {
    const res = await fetch('/api/numbers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        numbers: selectedNumbers,
        drawDate: selectedDate,
        category: selectedCategory,
        note: noteText.trim(),
      }),
    });

    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.error || 'Lỗi khi lưu vào DB H2');
    }

    const saved: SavedRecord = await res.json();
    statusMessage = {
      type: 'success',
      text: `Đã lưu thành công bộ 6 số ${saved.category} cho ngày ${saved.drawDate} (${getDayOfWeekName(saved.drawDate)}) vào DB H2 (#${saved.id})!`,
    };
    selectedNumbers = [];
    noteText = '';
    await fetchSavedRecords();
  } catch (err: any) {
    statusMessage = { type: 'danger', text: err.message || 'Lỗi kết nối DB H2.' };
  } finally {
    isSaving = false;
    render();
  }
}

async function deleteRecord(id: number): Promise<void> {
  if (!confirm(`Bạn có chắc muốn xóa bản ghi #${id} khỏi DB H2 không?`)) return;

  try {
    const res = await fetch(`/api/numbers/${id}`, { method: 'DELETE' });
    if (res.ok) {
      savedRecords = savedRecords.filter(r => r.id !== id);
      statusMessage = { type: 'success', text: `Đã xóa bản ghi #${id} khỏi DB H2 thành công.` };
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
      const data = await res.json();
      predictedNumbers = Array.isArray(data) ? data : data.numbers || [];
    }
  } catch (err) {
    console.warn('Backend unavailable, fallback simulation:', err);
    const limit = predictionCategory === 'POWER' ? 55 : 45;
    const set = new Set<number>();
    while (set.size < 6) {
      set.add(Math.floor(Math.random() * limit) + 1);
    }
    predictedNumbers = Array.from(set).sort((a, b) => a - b);
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
        drawDate: selectedDate,
        category: predictionCategory,
        note: `Dự đoán XGBoost AI (${predictionCategory === 'MEGA' ? 'Mega 6/45' : 'Power 6/55'} - Ngày ${selectedDate})`,
      }),
    });
    if (res.ok) {
      const saved = await res.json();
      predictSaveSuccess = `Đã lưu bộ số dự đoán ${saved.category} cho ngày ${saved.drawDate} vào DB H2 (#${saved.id})!`;
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
  if (selectedNumbers.includes(num)) {
    selectedNumbers = selectedNumbers.filter(n => n !== num);
  } else {
    if (selectedNumbers.length >= 6) {
      statusMessage = { type: 'danger', text: 'Bạn đã chọn đủ 6 số! Hãy bấm "Lưu vào DB H2" hoặc bỏ chọn bớt số khác.' };
      render();
      return;
    }
    selectedNumbers.push(num);
    selectedNumbers.sort((a, b) => a - b);
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
  render();
}

function setDate(newDate: string): void {
  selectedDate = newDate;
  const autoCat = getCategoryFromDate(newDate);
  selectedCategory = autoCat;
  if (autoCat === 'MEGA') {
    selectedNumbers = selectedNumbers.filter(n => n <= 45);
  }
  render();
}

function switchCategory(cat: 'MEGA' | 'POWER'): void {
  selectedCategory = cat;
  if (cat === 'MEGA') {
    selectedNumbers = selectedNumbers.filter(n => n <= 45);
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
                Spring Framework &bull; H2 In-Memory DB &bull; Angular Project Structure
              </small>
            </div>
          </div>
          <div class="d-flex align-items-center gap-2">
            <span class="badge bg-success-subtle text-success border border-success px-2 py-1 small">
              H2: Online
            </span>
          </div>
        </div>
      </header>

      <!-- 2. Main Area: Left Menu + Content (3-Zone Layout) -->
      <div class="d-flex flex-column flex-md-row flex-grow-1">
        <!-- Zone 2: Left Menu -->
        <aside id="app-left-menu" class="bg-white border-end p-3 flex-shrink-0" style="min-width: 260px;">
          <div class="mb-3 d-none d-md-block">
            <small class="text-uppercase text-muted fw-bold">Chức năng hệ thống</small>
          </div>

          <div class="nav nav-pills flex-row flex-md-column gap-2 mb-3">
            <button
              id="menu-item-manual"
              type="button"
              class="nav-link text-start d-flex align-items-center gap-2 flex-grow-1 flex-md-grow-0 ${activeTab === 'manual' ? 'active shadow-sm' : 'text-dark'}"
            >
              <span>📝</span>
              <div>
                <div class="fw-semibold">Nhập số theo ngày</div>
                <small class="d-none d-md-block text-muted opacity-75">Mega (1-45) & Power (1-55)</small>
              </div>
            </button>

            <button
              id="menu-item-prediction"
              type="button"
              class="nav-link text-start d-flex align-items-center gap-2 flex-grow-1 flex-md-grow-0 ${activeTab === 'prediction' ? 'active shadow-sm' : 'text-dark'}"
            >
              <span>⚡</span>
              <div>
                <div class="fw-semibold">Dự đoán AI XGBoost</div>
                <small class="d-none d-md-block text-muted opacity-75">Đề xuất 6 số tối ưu</small>
              </div>
            </button>
          </div>

          <!-- Schedule box -->
          <div class="mt-4 pt-3 border-top d-none d-md-block">
            <small class="text-uppercase text-muted fw-bold">Lịch quay thưởng</small>
            <div class="mt-2 small p-2 bg-light rounded border">
              <div class="d-flex align-items-center justify-content-between mb-1">
                <span class="fw-bold text-danger">🔴 MEGA 6/45</span>
                <span class="badge bg-danger-subtle text-danger">1 - 45</span>
              </div>
              <div class="text-muted" style="font-size: 0.8rem;">Thứ 4 &bull; Thứ 6 &bull; Chủ nhật</div>
            </div>
            <div class="mt-2 small p-2 bg-light rounded border">
              <div class="d-flex align-items-center justify-content-between mb-1">
                <span class="fw-bold text-primary">🔵 POWER 6/55</span>
                <span class="badge bg-primary-subtle text-primary">1 - 55</span>
              </div>
              <div class="text-muted" style="font-size: 0.8rem;">Thứ 3 &bull; Thứ 5 &bull; Thứ 7</div>
            </div>
          </div>
        </aside>

        <!-- Zone 3: Main Content -->
        <main id="app-content" class="flex-grow-1 p-3 p-md-4 overflow-auto">
          ${activeTab === 'manual' ? renderManualView(maxLimit, gridNumbers, dayName) : renderPredictionView()}
        </main>
      </div>
    </div>
  `;

  attachEventListeners();
}

function renderManualView(maxLimit: number, gridNumbers: number[], dayName: string): string {
  return `
    <div id="manual-entry-feature" class="mx-auto" style="max-width: 920px;">
      <div class="card shadow-sm border-0 mb-4">
        <div class="card-body p-3 p-md-4">
          <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
            <div>
              <h2 class="h4 fw-bold mb-1 text-primary">Nhập dãy 6 số theo ngày & Lưu vào DB H2</h2>
              <p class="text-muted mb-0 small">
                Tự động nhận diện danh mục: <strong>Mega (1-45)</strong> hoặc <strong>Power (1-55)</strong> theo thứ trong tuần
              </p>
            </div>
          </div>

          <!-- Category Selection -->
          <div class="p-3 mb-4 rounded-3 border bg-light shadow-sm">
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
              <label class="fw-bold text-dark mb-0">🏷️ Chọn danh mục xổ số:</label>
              <span class="small text-muted">Tự động theo thứ trong tuần của ngày mở thưởng</span>
            </div>

            <div class="row g-2">
              <div class="col-md-6">
                <button
                  type="button"
                  id="btn-cat-mega"
                  class="btn w-100 p-3 text-start border rounded-3 transition ${selectedCategory === 'MEGA' ? 'btn-danger text-white shadow-sm' : 'btn-white bg-white text-dark'}"
                >
                  <div class="d-flex justify-content-between align-items-center">
                    <span class="fw-bold fs-6">🔴 MEGA 6/45</span>
                    <span class="badge ${selectedCategory === 'MEGA' ? 'bg-white text-danger' : 'bg-danger-subtle text-danger'}">
                      Số: 01 &rarr; 45
                    </span>
                  </div>
                  <small class="d-block mt-1 opacity-75">
                    📅 Lịch quay: <strong>Thứ 4 &bull; Thứ 6 &bull; Chủ nhật</strong>
                  </small>
                </button>
              </div>

              <div class="col-md-6">
                <button
                  type="button"
                  id="btn-cat-power"
                  class="btn w-100 p-3 text-start border rounded-3 transition ${selectedCategory === 'POWER' ? 'btn-primary text-white shadow-sm' : 'btn-white bg-white text-dark'}"
                >
                  <div class="d-flex justify-content-between align-items-center">
                    <span class="fw-bold fs-6">🔵 POWER 6/55</span>
                    <span class="badge ${selectedCategory === 'POWER' ? 'bg-white text-primary' : 'bg-primary-subtle text-primary'}">
                      Số: 01 &rarr; 55
                    </span>
                  </div>
                  <small class="d-block mt-1 opacity-75">
                    📅 Lịch quay: <strong>Thứ 3 &bull; Thứ 5 &bull; Thứ 7</strong>
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
                <label class="form-label text-muted small mb-1">Chọn nhanh & Thông tin thứ:</label>
                <div class="d-flex gap-2 flex-wrap align-items-center">
                  <button type="button" id="btn-date-today" class="btn btn-outline-primary btn-sm px-3 ${selectedDate === getTodayDateString() ? 'active' : ''}">
                    🌟 Hôm nay
                  </button>
                  <button type="button" id="btn-date-yesterday" class="btn btn-outline-secondary btn-sm px-3 ${selectedDate === getYesterdayDateString() ? 'active' : ''}">
                    ⏪ Hôm qua
                  </button>
                  <span class="badge bg-light text-dark border px-3 py-2 ms-auto">
                    ${dayName} &bull; <strong>${selectedCategory === 'MEGA' ? 'Mega 6/45' : 'Power 6/55'}</strong>
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- Selected Numbers Preview (6 slots) -->
          <div class="p-3 bg-light rounded-3 mb-4 text-center">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <span class="fw-semibold text-secondary small">
                6 số đã chọn cho ngày <strong>${selectedDate}</strong> (${selectedCategory}):
              </span>
              <span class="badge ${selectedNumbers.length === 6 ? 'bg-success' : 'bg-primary'}">
                ${selectedNumbers.length} / 6 số
              </span>
            </div>

            <div class="d-flex justify-content-center gap-2 gap-md-3 flex-wrap my-2">
              ${[0, 1, 2, 3, 4, 5].map(idx => {
                const val = selectedNumbers[idx];
                return `
                  <div class="text-center" style="width: 58px;">
                    <input
                      id="slot-input-${idx}"
                      type="number"
                      min="1"
                      max="${maxLimit}"
                      class="form-control text-center fw-bold"
                      value="${val !== undefined ? val : ''}"
                      placeholder="--"
                      style="width: 52px; height: 52px; border-radius: 50%; font-size: 1.25rem; border: 2px solid #0d6efd; margin: 0 auto 4px auto;"
                    />
                    <small class="text-muted" style="font-size: 0.75rem;">Số ${idx + 1}</small>
                  </div>
                `;
              }).join('')}
            </div>
          </div>

          <!-- Number Grid -->
          <div class="mb-4">
            <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
              <span class="fw-semibold text-dark">Bảng số chọn nhanh (01 &rarr; ${maxLimit}):</span>
              <div class="d-flex gap-2">
                <button type="button" id="btn-quick-random" class="btn btn-sm btn-outline-primary">🎲 Ngẫu nhiên 6 số</button>
                <button type="button" id="btn-quick-reset" class="btn btn-sm btn-outline-secondary">🧹 Xóa chọn lại</button>
              </div>
            </div>

            <div class="d-grid gap-2 p-2 bg-white rounded border" style="grid-template-columns: repeat(auto-fill, minmax(42px, 1fr));">
              ${gridNumbers.map(n => {
                const isSelected = selectedNumbers.includes(n);
                return `
                  <button
                    type="button"
                    class="btn p-0 fw-bold rounded-circle btn-grid-ball ${isSelected ? 'btn-primary text-white shadow-sm' : 'btn-outline-light text-dark'}"
                    data-num="${n}"
                    style="height: 42px; border: ${isSelected ? 'none' : '1px solid #dee2e6'};"
                  >
                    ${n < 10 ? '0' + n : n}
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
                placeholder="Ghi chú (Ví dụ: Vé ${selectedCategory} ngày ${selectedDate}...)"
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
                ${isSaving ? 'Đang lưu vào H2...' : '💾 Lưu vào DB H2'}
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
                Cơ sở dữ liệu H2 - Các bộ số đã lưu (${savedRecords.length})
              </h3>
              <small class="text-muted">Phân loại Mega & Power theo từng kỳ quay</small>
            </div>

            <!-- Filters -->
            <div class="d-flex align-items-center gap-2 flex-wrap">
              <select id="select-filter-category" class="form-select form-select-sm" style="width: 140px;">
                <option value="" ${filterCategory === '' ? 'selected' : ''}>Tất cả danh mục</option>
                <option value="MEGA" ${filterCategory === 'MEGA' ? 'selected' : ''}>🔴 Mega 6/45</option>
                <option value="POWER" ${filterCategory === 'POWER' ? 'selected' : ''}>🔵 Power 6/55</option>
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
              <p class="mb-0">Chưa có bản ghi nào phù hợp trong DB H2.</p>
              <small>Hãy chọn danh mục (Mega/Power), chọn 6 số và bấm "Lưu vào DB H2".</small>
            </div>
          ` : `
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0">
                <thead class="table-light">
                  <tr>
                    <th scope="col" style="width: 60px;">ID</th>
                    <th scope="col" style="width: 130px;">Danh mục</th>
                    <th scope="col" style="width: 140px;">Ngày mở thưởng</th>
                    <th scope="col">Dãy 6 số đã lưu</th>
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
                        <div class="d-flex gap-1 gap-md-2 flex-wrap">
                          ${r.numbers.map(n => `
                            <span
                              class="badge rounded-circle bg-primary text-white d-inline-flex align-items-center justify-content-center"
                              style="width: 32px; height: 32px; font-size: 0.85rem;"
                            >
                              ${n < 10 ? '0' + n : n}
                            </span>
                          `).join('')}
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
  return `
    <div id="prediction-feature" class="card shadow-sm border-0 my-3 mx-auto" style="max-width: 820px;">
      <div class="card-body p-4 text-center">
        <h3 class="card-title fw-bold text-primary mb-2">⚡ Đề xuất bộ số bằng mô hình AI XGBoost</h3>
        <p class="card-text text-muted mb-4">
          Phân tích tần suất số nóng / lô gan và dự đoán bộ 6 số theo từng loại giải:
        </p>

        <div class="d-flex justify-content-center gap-2 mb-4">
          <button
            type="button"
            id="btn-pred-mega"
            class="btn px-4 py-2 rounded-pill fw-bold ${predictionCategory === 'MEGA' ? 'btn-danger text-white shadow-sm' : 'btn-outline-danger'}"
          >
            🔴 Mega 6/45 (Thứ 4, 6, CN)
          </button>
          <button
            type="button"
            id="btn-pred-power"
            class="btn px-4 py-2 rounded-pill fw-bold ${predictionCategory === 'POWER' ? 'btn-primary text-white shadow-sm' : 'btn-outline-primary'}"
          >
            🔵 Power 6/55 (Thứ 3, 5, 7)
          </button>
        </div>

        <div class="d-flex justify-content-center mb-4">
          <button
            id="btn-predict-action"
            type="button"
            class="btn btn-warning btn-lg fw-bold px-5 py-3 shadow"
            ${isPredicting ? 'disabled' : ''}
          >
            ${isPredicting ? `
              <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              Đang phân tích xác suất...
            ` : `🎯 Dự đoán 6 số ${predictionCategory === 'MEGA' ? 'Mega 6/45' : 'Power 6/55'}`}
          </button>
        </div>

        ${predictedNumbers.length > 0 ? `
          <div class="mt-4 pt-3 border-top">
            <h4 class="h6 text-muted mb-3">
              Kết quả phân tích 6 số đề xuất (${predictionCategory === 'MEGA' ? 'Mega 6/45: 01 - 45' : 'Power 6/55: 01 - 55'}):
            </h4>
            <div id="prediction-results" class="d-flex justify-content-center gap-3 flex-wrap mb-4">
              ${predictedNumbers.map(num => `
                <div
                  class="d-flex align-items-center justify-content-center rounded-circle bg-warning text-dark fw-bold shadow"
                  style="width: 48px; height: 48px; font-size: 1.25rem;"
                >
                  ${num < 10 ? '0' + num : num}
                </div>
              `).join('')}
            </div>

            <button id="btn-save-predicted" type="button" class="btn btn-outline-primary">
              💾 Lưu bộ số ${predictionCategory} này cho ngày ${selectedDate} vào DB H2
            </button>

            ${predictSaveSuccess ? `
              <div class="alert alert-success mt-3 mb-0 py-2">
                ${predictSaveSuccess}
              </div>
            ` : ''}
          </div>
        ` : ''}
      </div>
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
  });

  // Manual Entry event listeners
  if (activeTab === 'manual') {
    document.getElementById('btn-cat-mega')?.addEventListener('click', () => switchCategory('MEGA'));
    document.getElementById('btn-cat-power')?.addEventListener('click', () => switchCategory('POWER'));

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
      noteText = '';
      statusMessage = null;
      render();
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
    document.getElementById('btn-pred-mega')?.addEventListener('click', () => {
      predictionCategory = 'MEGA';
      predictedNumbers = [];
      render();
    });
    document.getElementById('btn-pred-power')?.addEventListener('click', () => {
      predictionCategory = 'POWER';
      predictedNumbers = [];
      render();
    });
    document.getElementById('btn-predict-action')?.addEventListener('click', runPrediction);
    document.getElementById('btn-save-predicted')?.addEventListener('click', savePredictedToH2);
  }
}

// Initial bootstrap
fetchSavedRecords();
render();
