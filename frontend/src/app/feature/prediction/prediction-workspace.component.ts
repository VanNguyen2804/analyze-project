import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-prediction-workspace', standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <section class="workspace">
      <header class="workspace-heading">
        <div><span class="eyebrow">VIETLOTT · PHÂN TÍCH & THEO DÕI</span><h1>Dự đoán & kết quả</h1>
          <p>Tạo bộ số trước kỳ quay, theo dõi bản đã chốt và đối chiếu kết quả thực tế.</p></div>
      </header>
      <nav class="workspace-tabs" aria-label="Chức năng dự đoán">
        <a routerLink="/analyze" routerLinkActive="selected" [routerLinkActiveOptions]="{exact:true}" ariaCurrentWhenActive="page">
          <span class="step">01</span><span><strong>Tạo dự đoán</strong><small>Xem bộ số và lý do lựa chọn</small></span>
        </a>
        <a routerLink="/analyze/history" routerLinkActive="selected" ariaCurrentWhenActive="page">
          <span class="step">02</span><span><strong>Lịch sử & đối soát</strong><small>Theo dõi bộ đã chốt và số khớp</small></span>
        </a>
      </nav>
      <router-outlet />
    </section>`,
  styles: [`
    .workspace{max-width:1440px;margin:auto;padding:24px 12px}.eyebrow{font-size:11px;font-weight:700;letter-spacing:.13em;color:#54708f}
    h1{font-size:28px;color:#152e4a;letter-spacing:-.7px;margin:7px 0}p{color:#63758a;margin-bottom:20px;font-size:14px}
    .workspace-tabs{display:flex;gap:8px;padding:6px;background:#eaf0f6;border:1px solid #dfe7ef;border-radius:14px;margin-bottom:22px}
    .workspace-tabs a{flex:1;display:flex;align-items:center;gap:12px;color:#5a6d83;text-decoration:none;padding:14px 18px;border-radius:10px}
    .workspace-tabs a.selected{background:white;color:#174e93;box-shadow:0 2px 8px #203c5810}.step{font-weight:750;font-size:13px;border-radius:9px;background:#dce5ef;padding:8px}
    .selected .step{background:#e8f1ff}.workspace-tabs strong,.workspace-tabs small{display:block}.workspace-tabs strong{font-size:14px}.workspace-tabs small{font-size:12px;margin-top:3px}
    a:focus-visible{outline:3px solid #6799df;outline-offset:2px}@media(max-width:600px){.workspace{padding:16px 0}.workspace-tabs a{padding:12px 8px;gap:8px}.workspace-tabs small{display:none}h1{font-size:24px}}
  `]
})
export class PredictionWorkspaceComponent {}
