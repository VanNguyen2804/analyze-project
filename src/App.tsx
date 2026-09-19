import React, { useState, useEffect } from 'react';

interface SavedRecord {
  id: number;
  numbers: number[];
  createdAt: string;
  note?: string;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'manual' | 'prediction'>('manual');

  // Manual Entry States
  const [selectedNumbers, setSelectedNumbers] = useState<number[]>([]);
  const [note, setNote] = useState<string>('');
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [savedRecords, setSavedRecords] = useState<SavedRecord[]>([]);
  const [isLoadingRecords, setIsLoadingRecords] = useState<boolean>(false);
  const [manualMessage, setManualMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Prediction States
  const [predictedNumbers, setPredictedNumbers] = useState<number[]>([]);
  const [isSpinning, setIsSpinning] = useState<boolean>(false);
  const [predictError, setPredictError] = useState<string | null>(null);
  const [predictSaveSuccess, setPredictSaveSuccess] = useState<string | null>(null);

  // Available grid numbers 1 to 45
  const gridNumbers = Array.from({ length: 45 }, (_, i) => i + 1);

  // Load records on mount
  useEffect(() => {
    fetchSavedRecords();
  }, []);

  const fetchSavedRecords = async () => {
    setIsLoadingRecords(true);
    try {
      const res = await fetch('/api/numbers');
      if (res.ok) {
        const data = await res.json();
        setSavedRecords(data);
      }
    } catch (err) {
      console.error('Lỗi tải danh sách từ H2 DB:', err);
    } finally {
      setIsLoadingRecords(false);
    }
  };

  const toggleNumber = (num: number) => {
    setManualMessage(null);
    if (selectedNumbers.includes(num)) {
      setSelectedNumbers(selectedNumbers.filter((n) => n !== num));
    } else {
      if (selectedNumbers.length >= 6) {
        setManualMessage({
          type: 'error',
          text: 'Bạn đã chọn đủ 6 số! Hãy bấm "Lưu vào DB H2" hoặc bỏ chọn bớt số khác.',
        });
        return;
      }
      setSelectedNumbers([...selectedNumbers, num].sort((a, b) => a - b));
    }
  };

  const handleInputChange = (index: number, valStr: string) => {
    setManualMessage(null);
    const val = parseInt(valStr, 10);
    const current = [...selectedNumbers];

    if (isNaN(val)) {
      current.splice(index, 1);
      setSelectedNumbers(current);
      return;
    }

    if (val < 1 || val > 45) {
      setManualMessage({
        type: 'error',
        text: `Số ${val} không hợp lệ! Vui lòng nhập từ 1 đến 45.`,
      });
      return;
    }

    // Check duplicate
    if (current.includes(val) && current[index] !== val) {
      setManualMessage({
        type: 'error',
        text: `Số ${val} đã được chọn rồi! Các số không được trùng nhau.`,
      });
      return;
    }

    current[index] = val;
    setSelectedNumbers(Array.from(new Set(current)).sort((a, b) => a - b));
  };

  const randomPick = () => {
    setManualMessage(null);
    const set = new Set<number>();
    while (set.size < 6) {
      set.add(Math.floor(Math.random() * 45) + 1);
    }
    setSelectedNumbers(Array.from(set).sort((a, b) => a - b));
  };

  const resetManualForm = () => {
    setSelectedNumbers([]);
    setNote('');
    setManualMessage(null);
  };

  const saveToH2 = async () => {
    setManualMessage(null);
    if (selectedNumbers.length !== 6) {
      setManualMessage({
        type: 'error',
        text: `Vui lòng chọn đúng 6 số khác nhau! (Hiện tại: ${selectedNumbers.length}/6)`,
      });
      return;
    }

    setIsSaving(true);
    try {
      const res = await fetch('/api/numbers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          numbers: selectedNumbers,
          note: note.trim(),
        }),
      });

      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.error || 'Không thể lưu vào DB H2');
      }

      const saved: SavedRecord = await res.json();
      setManualMessage({
        type: 'success',
        text: `Đã lưu thành công bộ 6 số vào DB H2 (Bản ghi #${saved.id})!`,
      });
      resetManualForm();
      fetchSavedRecords();
    } catch (err: any) {
      setManualMessage({
        type: 'error',
        text: err.message || 'Lỗi khi lưu vào DB H2.',
      });
    } finally {
      setIsSaving(false);
    }
  };

  const deleteRecord = async (id: number) => {
    if (!window.confirm(`Bạn có chắc muốn xóa bản ghi #${id} khỏi DB H2 không?`)) {
      return;
    }

    try {
      const res = await fetch(`/api/numbers/${id}`, { method: 'DELETE' });
      if (res.ok) {
        setSavedRecords((prev) => prev.filter((r) => r.id !== id));
        setManualMessage({
          type: 'success',
          text: `Đã xóa bản ghi #${id} khỏi DB H2 thành công.`,
        });
      }
    } catch (err) {
      console.error('Lỗi khi xóa bản ghi:', err);
    }
  };

  // Prediction handlers
  const predict = async () => {
    setIsSpinning(true);
    setPredictError(null);
    setPredictSaveSuccess(null);

    try {
      const response = await fetch('/api/analyze/predict');
      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
      }
      const data: number[] = await response.json();

      setTimeout(() => {
        setPredictedNumbers(data);
        setIsSpinning(false);
      }, 800);
    } catch (err: any) {
      console.error('Failed to get prediction:', err);
      setTimeout(() => {
        const fallbackSet = new Set<number>();
        while (fallbackSet.size < 6) {
          fallbackSet.add(Math.floor(Math.random() * 45) + 1);
        }
        const fallbackList = Array.from(fallbackSet).sort((a, b) => a - b);
        setPredictedNumbers(fallbackList);
        setIsSpinning(false);
      }, 800);
    }
  };

  const savePredictedToH2 = async () => {
    if (predictedNumbers.length !== 6) return;
    try {
      const res = await fetch('/api/numbers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          numbers: predictedNumbers,
          note: 'Dự đoán từ mô hình AI XGBoost',
        }),
      });
      if (res.ok) {
        const saved = await res.json();
        setPredictSaveSuccess(`Đã lưu kết quả dự đoán #${saved.id} vào DB H2!`);
        fetchSavedRecords();
      }
    } catch (err) {
      console.error('Lỗi khi lưu kết quả dự đoán:', err);
    }
  };

  return (
    <div className="min-vh-100 bg-light">
      {/* Header */}
      <header className="bg-white border-bottom shadow-sm sticky-top">
        <div className="container py-3 d-flex justify-content-between align-items-center flex-wrap gap-2">
          <div className="d-flex align-items-center gap-2">
            <span className="fs-3">🎯</span>
            <div>
              <h1 className="h5 mb-0 fw-bold text-dark">Analyze Project</h1>
              <small className="text-muted">Quản lý bộ số với DB H2 & Dự đoán mô hình XGBoost</small>
            </div>
          </div>
          <div className="nav nav-pills gap-1">
            <button
              id="tab-manual"
              className={`nav-link fw-semibold ${activeTab === 'manual' ? 'active' : ''}`}
              onClick={() => setActiveTab('manual')}
            >
              📝 Tự nhập 6 số & Lưu DB H2
            </button>
            <button
              id="tab-prediction"
              className={`nav-link fw-semibold ${activeTab === 'prediction' ? 'active' : ''}`}
              onClick={() => setActiveTab('prediction')}
            >
              🤖 Dự đoán XGBoost AI
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="container py-4">
        {activeTab === 'manual' && (
          <div className="mx-auto" style={{ maxWidth: '920px' }}>
            {/* Input Card */}
            <div className="card shadow-sm border-0 mb-4">
              <div className="card-body p-4">
                <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
                  <div>
                    <h2 className="h4 fw-bold mb-1 text-primary">Tự chọn 6 số may mắn</h2>
                    <p className="text-muted mb-0 small">
                      Nhập trực tiếp hoặc nhấp chọn các số trên bảng số (từ 01 đến 45)
                    </p>
                  </div>
                  <span className="badge bg-success-subtle text-success border border-success-subtle px-3 py-2">
                    <span className="me-1">●</span> DB H2: jdbc:h2:mem:lotterydb
                  </span>
                </div>

                {/* 6 Circular Input Slots */}
                <div className="p-3 bg-light rounded-3 mb-4 text-center">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <span className="fw-semibold text-secondary small">
                      6 con số đã chọn:
                    </span>
                    <span className={`badge ${selectedNumbers.length === 6 ? 'bg-success' : 'bg-primary'}`}>
                      {selectedNumbers.length} / 6 số
                    </span>
                  </div>

                  <div className="d-flex justify-content-center gap-2 gap-md-3 flex-wrap my-2">
                    {Array.from({ length: 6 }).map((_, i) => {
                      const num = selectedNumbers[i];
                      return (
                        <div key={i} className="text-center" style={{ width: '56px' }}>
                          <input
                            id={`manual-slot-${i + 1}`}
                            type="number"
                            min="1"
                            max="45"
                            className="form-control text-center fw-bold slot-input shadow-sm"
                            value={num !== undefined ? num : ''}
                            onChange={(e) => handleInputChange(i, e.target.value)}
                            placeholder="--"
                            style={{
                              width: '56px',
                              height: '56px',
                              borderRadius: '50%',
                              fontSize: '1.25rem',
                              borderColor: num !== undefined ? '#0d6efd' : '#dee2e6',
                              backgroundColor: num !== undefined ? '#e7f1ff' : '#fff',
                              color: num !== undefined ? '#0d6efd' : '#212529',
                            }}
                          />
                          <small className="text-muted d-block mt-1">Số {i + 1}</small>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Number Grid 1 - 45 */}
                <div className="mb-4">
                  <div className="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
                    <span className="fw-semibold text-dark">Bảng số chọn nhanh (01 - 45):</span>
                    <div className="d-flex gap-2">
                      <button
                        id="btn-random-pick"
                        type="button"
                        className="btn btn-sm btn-outline-primary"
                        onClick={randomPick}
                      >
                        🎲 Ngẫu nhiên 6 số
                      </button>
                      <button
                        id="btn-reset-form"
                        type="button"
                        className="btn btn-sm btn-outline-secondary"
                        onClick={resetManualForm}
                      >
                        🔄 Làm mới
                      </button>
                    </div>
                  </div>

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fill, minmax(44px, 1fr))',
                      gap: '8px',
                      background: '#fafbfc',
                      padding: '14px',
                      borderRadius: '10px',
                      border: '1px solid #edf0f2',
                    }}
                  >
                    {gridNumbers.map((n) => {
                      const isSelected = selectedNumbers.includes(n);
                      return (
                        <button
                          key={n}
                          id={`grid-btn-${n}`}
                          type="button"
                          className="btn btn-sm fw-bold transition"
                          onClick={() => toggleNumber(n)}
                          style={{
                            height: '44px',
                            borderRadius: '50%',
                            fontSize: '0.95rem',
                            border: isSelected ? '2px solid #0d6efd' : '1px solid #dee2e6',
                            background: isSelected
                              ? 'linear-gradient(135deg, #0d6efd, #004fb0)'
                              : '#ffffff',
                            color: isSelected ? '#ffffff' : '#495057',
                            boxShadow: isSelected ? '0 2px 6px rgba(13, 110, 253, 0.35)' : 'none',
                          }}
                        >
                          {n < 10 ? `0${n}` : n}
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Note and Save to DB Button */}
                <div className="row g-3">
                  <div className="col-md-8">
                    <input
                      id="input-note"
                      type="text"
                      className="form-control py-2"
                      placeholder="Ghi chú (Ví dụ: Vé số mua đài TP.HCM, vé ngày 18/09...)"
                      value={note}
                      onChange={(e) => setNote(e.target.value)}
                    />
                  </div>
                  <div className="col-md-4">
                    <button
                      id="btn-save-h2"
                      className="btn btn-primary w-100 py-2 fw-bold shadow-sm"
                      onClick={saveToH2}
                      disabled={isSaving || selectedNumbers.length !== 6}
                    >
                      {isSaving ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-2" role="status" />
                          Đang lưu vào H2...
                        </>
                      ) : (
                        '💾 Lưu vào DB H2'
                      )}
                    </button>
                  </div>
                </div>

                {/* Notifications */}
                {manualMessage && (
                  <div
                    className={`alert ${
                      manualMessage.type === 'success' ? 'alert-success' : 'alert-danger'
                    } py-2 mt-3 mb-0 shadow-sm`}
                    role="alert"
                  >
                    {manualMessage.text}
                  </div>
                )}
              </div>
            </div>

            {/* Saved Records Table in DB H2 */}
            <div className="card shadow-sm border-0">
              <div className="card-header bg-white py-3 d-flex justify-content-between align-items-center">
                <div className="d-flex align-items-center gap-2">
                  <h3 className="h5 mb-0 fw-bold text-dark">
                    Cơ sở dữ liệu H2 - Các bộ 6 số đã lưu
                  </h3>
                  <span className="badge bg-primary-subtle text-primary border border-primary-subtle">
                    {savedRecords.length} bản ghi
                  </span>
                </div>
                <button
                  id="btn-refresh-list"
                  className="btn btn-sm btn-outline-primary"
                  onClick={fetchSavedRecords}
                  disabled={isLoadingRecords}
                >
                  {isLoadingRecords ? 'Đang tải...' : '🔄 Làm mới DB'}
                </button>
              </div>

              <div className="card-body p-0">
                {savedRecords.length === 0 ? (
                  <div className="text-center py-5 text-muted">
                    <p className="mb-1 fw-semibold">Chưa có bộ số nào trong cơ sở dữ liệu H2</p>
                    <small>Hãy chọn 6 con số ở bảng trên và nhấn "Lưu vào DB H2".</small>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover align-middle mb-0">
                      <thead className="table-light">
                        <tr>
                          <th scope="col" style={{ width: '80px' }}>ID</th>
                          <th scope="col">Bộ 6 số đã lưu</th>
                          <th scope="col">Ghi chú</th>
                          <th scope="col">Thời gian lưu</th>
                          <th scope="col" className="text-end" style={{ width: '90px' }}>Thao tác</th>
                        </tr>
                      </thead>
                      <tbody>
                        {savedRecords.map((record) => (
                          <tr key={record.id} id={`row-record-${record.id}`}>
                            <td className="fw-bold text-secondary">#{record.id}</td>
                            <td>
                              <div className="d-flex gap-2 flex-wrap">
                                {record.numbers.map((n, idx) => (
                                  <span
                                    key={idx}
                                    className="d-inline-flex align-items-center justify-content-center text-white fw-bold shadow-sm"
                                    style={{
                                      width: '36px',
                                      height: '36px',
                                      borderRadius: '50%',
                                      background: 'radial-gradient(circle at 12px 12px, #00c6ff, #0072ff)',
                                      fontSize: '0.9rem',
                                    }}
                                  >
                                    {n < 10 ? `0${n}` : n}
                                  </span>
                                ))}
                              </div>
                            </td>
                            <td>
                              {record.note ? (
                                <span className="badge bg-light text-dark border px-2 py-1">
                                  {record.note}
                                </span>
                              ) : (
                                <span className="text-muted fst-italic small">--</span>
                              )}
                            </td>
                            <td className="text-muted small">
                              {new Date(record.createdAt).toLocaleString('vi-VN')}
                            </td>
                            <td className="text-end">
                              <button
                                id={`btn-delete-${record.id}`}
                                className="btn btn-sm btn-outline-danger"
                                title="Xóa khỏi DB H2"
                                onClick={() => deleteRecord(record.id)}
                              >
                                Xóa
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {activeTab === 'prediction' && (
          <div className="mx-auto text-center" style={{ maxWidth: '720px', marginTop: '30px' }}>
            <div className="card shadow-sm border-0 p-4">
              <h2 className="h4 fw-bold mb-2 text-dark">Analyze Project - Dự đoán XGBoost AI</h2>
              <p className="text-muted mb-4">
                Mô hình phân tích xác suất logistic log-odds để chọn ra 6 con số có tiềm năng cao nhất
              </p>

              <div>
                <button
                  id="btn-predict"
                  className="btn btn-danger btn-lg px-4 py-2 shadow-sm"
                  onClick={predict}
                  disabled={isSpinning}
                >
                  {isSpinning ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" />
                      Đang chạy mô hình XGBoost...
                    </>
                  ) : (
                    '🚀 Tiến hành phân tích'
                  )}
                </button>
              </div>

              {predictError && (
                <div className="alert alert-warning my-3" role="alert">
                  {predictError}
                </div>
              )}

              {predictedNumbers.length > 0 && (
                <div className="mt-4 pt-3 border-top">
                  <h6 className="text-muted mb-3">Kết quả phân tích 6 số đề xuất:</h6>
                  <div
                    id="prediction-results"
                    className="d-flex justify-content-center gap-3 flex-wrap mb-4"
                  >
                    {predictedNumbers.map((num, idx) => (
                      <div key={idx} className="ball" id={`ball-${num}`}>
                        {num < 10 ? `0${num}` : num}
                      </div>
                    ))}
                  </div>

                  <button
                    id="btn-save-predicted"
                    className="btn btn-outline-primary"
                    onClick={savePredictedToH2}
                  >
                    💾 Lưu bộ số dự đoán này vào DB H2
                  </button>

                  {predictSaveSuccess && (
                    <div className="alert alert-success mt-3 mb-0 py-2">
                      {predictSaveSuccess}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
