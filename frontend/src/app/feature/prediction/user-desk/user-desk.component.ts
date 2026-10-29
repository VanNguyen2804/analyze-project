import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnChanges,
  inject,
  input,
  output,
  signal,
  computed,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, takeUntil } from 'rxjs';
import { WorkspaceApiService } from '@app/core/services/workspace-api.service';
import {
  WorkspaceBatch,
  WorkspaceSuggestion,
  WorkspaceTicket,
} from '@app/core/models/workspace.model';

interface DraftTicket {
  key: string;
  sourceId: string | null;
  label: string;
  text: string;
  original?: number[];
}
interface Draft {
  view: WorkspaceSuggestion;
  rows: DraftTicket[];
  requestKey: string;
  requestPayload?: string;
  labCount?: number;
}

@Component({
  selector: 'app-user-desk',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe, DecimalPipe],
  templateUrl: './user-desk.component.html',
  styleUrl: './user-desk.component.css',
})
export class UserDeskComponent implements OnChanges {
  readonly suggestion = input.required<WorkspaceSuggestion>();
  readonly changed = output<WorkspaceSuggestion>();
  readonly view = signal<WorkspaceSuggestion | null>(null);
  readonly rows = signal<DraftTicket[]>([]);
  readonly batches = signal<WorkspaceBatch[]>([]);
  readonly error = signal('');
  readonly message = signal('');
  readonly busy = signal(false);
  readonly historyError = signal('');
  readonly labCount = signal(5);
  readonly concentration = computed(() => {
    const max = this.view()?.category === 'POWER' ? 55 : 45;
    const valid: number[][] = [];
    for (const row of this.rows()) {
      try {
        const numbers = this.parse(row.text);
        if (
          numbers.length === 6 &&
          new Set(numbers).size === 6 &&
          numbers.every((n) => Number.isInteger(n) && n >= 1 && n <= max)
        )
          valid.push(numbers);
      } catch {
        /* Incomplete rows are validated when the user commits. */
      }
    }
    let largestOverlap = 0;
    for (let i = 0; i < valid.length; i++)
      for (let j = 0; j < i; j++)
        largestOverlap = Math.max(
          largestOverlap,
          valid[i].filter((n) => valid[j].includes(n)).length,
        );
    return {
      validTickets: valid.length,
      uniqueNumbers: new Set(valid.flat()).size,
      largestOverlap,
    };
  });
  weight = 50;
  keep = '';
  exclude = '';
  private readonly api = inject(WorkspaceApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cancel = new Subject<void>();
  private readonly historyCancel = new Subject<void>();
  private inputId = '';
  private latestSuggestion: WorkspaceSuggestion | null = null;
  private requestKey: string = crypto.randomUUID();
  private requestPayload = '';
  private lastCategory = '';

  ngOnChanges() {
    const incoming = this.suggestion();
    if (incoming.id === this.inputId || incoming.id === this.view()?.id) return;
    this.latestSuggestion = incoming;
    this.inputId = incoming.id;
    this.cancel.next();
    this.busy.set(false);
    this.error.set('');
    this.message.set('');
    let draft: Draft | null = null;
    try {
      draft = JSON.parse(localStorage.getItem(this.storageKey(incoming.category)) || 'null');
    } catch {
      /* unavailable storage */
    }
    if (draft?.view?.targetDate === incoming.targetDate && draft.rows?.length) {
      this.activate(draft.view);
      this.rows.set(draft.rows);
      this.labCount.set(draft.labCount ?? 5);
      this.requestKey = draft.requestKey;
      this.requestPayload = draft.requestPayload || '';
      this.message.set(
        'Đang giữ vé nháp và nguồn gợi ý trước đó. Bạn có thể tiếp tục chỉnh hoặc bỏ nháp để dùng gợi ý mới.',
      );
      this.changed.emit(draft.view);
    } else {
      this.activate(incoming);
      this.rows.set([]);
      this.newRequest();
    }
    if (incoming.category !== this.lastCategory) {
      this.batches.set([]);
      this.lastCategory = incoming.category;
    }
    this.loadHistory();
  }
  private storageKey(category: string) {
    return `prediction-user-draft-v1:${category}`;
  }
  private activate(view: WorkspaceSuggestion) {
    this.view.set(view);
    this.weight = view.primaryWeight * 100;
    this.keep = view.keep.join(' ');
    this.exclude = view.exclude.join(' ');
  }
  private newRequest() {
    this.requestKey = crypto.randomUUID();
    this.requestPayload = '';
  }
  private saveDraft() {
    const view = this.view();
    if (!view) return;
    try {
      localStorage.setItem(
        this.storageKey(view.category),
        JSON.stringify({
          view,
          rows: this.rows(),
          requestKey: this.requestKey,
          requestPayload: this.requestPayload,
          labCount: this.labCount(),
        }),
      );
    } catch {
      this.message.set('Trình duyệt chưa lưu được bản nháp. Giữ trang mở cho đến khi chốt vé.');
    }
  }
  useLatest() {
    this.cancel.next();
    this.busy.set(false);
    this.activate(this.latestSuggestion ?? this.suggestion());
    this.rows.set([]);
    this.newRequest();
    this.saveDraft();
    this.changed.emit(this.view()!);
    this.message.set('Đã dùng gợi ý mới và bỏ danh sách nháp trước đó.');
  }
  algorithmTickets() {
    return this.view()?.tickets.filter((t) => t.source !== 'AI_LAB') ?? [];
  }
  async copy(ticket: WorkspaceTicket) {
    try {
      await navigator.clipboard.writeText(
        ticket.numbers.map((n) => String(n).padStart(2, '0')).join(' '),
      );
      this.message.set('Đã sao chép bộ số.');
    } catch {
      this.error.set('Chưa sao chép được. Bạn có thể chọn hoặc nhập lại bộ số.');
    }
  }
  labTickets() {
    return (
      this.view()
        ?.tickets.filter((t) => t.source === 'AI_LAB')
        .slice(0, this.labCount()) ?? []
    );
  }
  setLabCount(count: number) {
    if (![3, 4, 5].includes(count)) return;
    this.labCount.set(count);
    this.newRequest();
    this.saveDraft();
  }
  add(ticket?: WorkspaceTicket) {
    if (this.rows().length >= 25) {
      this.error.set('Tối đa 25 vé trong một bản chốt.');
      return;
    }
    this.rows.update((rows) => [
      ...rows,
      {
        key: crypto.randomUUID(),
        sourceId: ticket?.id ?? null,
        label: ticket?.label ?? 'Tự nhập',
        text: ticket?.numbers.join(' ') ?? '',
        original: ticket?.numbers,
      },
    ]);
    this.error.set('');
    this.newRequest();
    this.saveDraft();
  }
  edit(key: string, text: string) {
    this.rows.update((rows) => rows.map((r) => (r.key === key ? { ...r, text } : r)));
    this.newRequest();
    this.saveDraft();
  }
  remove(key: string) {
    this.rows.update((rows) => rows.filter((r) => r.key !== key));
    this.newRequest();
    this.saveDraft();
  }
  parse(text: string): number[] {
    if (!text.trim()) return [];
    const parts = text.trim().split(/[\s,;]+/);
    if (parts.some((part) => !/^\d+$/.test(part)))
      throw new Error('Chỉ nhập số nguyên, cách nhau bằng dấu cách hoặc dấu phẩy.');
    return parts.map(Number);
  }
  recombine() {
    const view = this.view();
    if (!view || this.busy() || this.rows().length) return;
    let keep: number[], exclude: number[];
    try {
      keep = this.parse(this.keep);
      exclude = this.parse(this.exclude);
    } catch (e) {
      this.error.set((e as Error).message);
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api
      .combine(view.id, this.weight / 100, keep, exclude)
      .pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          // Existing selected sources remain bound to the old immutable suggestion.
          if (this.rows().length) {
            this.error.set(
              'Bộ kết hợp mới đã được tính. Hãy bỏ các vé nháp hiện tại trước khi chuyển sang bộ gợi ý mới.',
            );
            this.busy.set(false);
            return;
          }
          this.activate(updated);
          this.changed.emit(updated);
          this.newRequest();
          this.saveDraft();
          this.busy.set(false);
        },
        error: (e) => {
          this.error.set(this.errorText(e));
          this.busy.set(false);
        },
      });
  }
  commit() {
    const view = this.view();
    if (!view || this.busy()) return;
    try {
      const seen = new Set<string>();
      const tickets = this.rows().map((row) => {
        const numbers = this.parse(row.text).sort((a, b) => a - b);
        if (
          numbers.length !== 6 ||
          new Set(numbers).size !== 6 ||
          numbers.some(
            (n) => !Number.isInteger(n) || n < 1 || n > (view.category === 'MEGA' ? 45 : 55),
          )
        )
          throw new Error('Mỗi vé cần sáu số khác nhau trong miền của loại xổ số.');
        const key = numbers.join(',');
        if (seen.has(key)) throw new Error('Có vé trùng nhau trong danh sách.');
        seen.add(key);
        return { sourceId: row.sourceId, numbers };
      });
      if (!tickets.length) throw new Error('Chọn hoặc nhập ít nhất một vé.');
      const payload = {
        category: view.category,
        targetDate: view.targetDate,
        suggestionId: view.id,
        expectedRevision: view.expectedRevision,
        tickets,
        labCount: this.labCount(),
      };
      const serialized = JSON.stringify(payload);
      if (this.requestPayload && this.requestPayload !== serialized) this.newRequest();
      this.requestPayload = serialized;
      this.saveDraft();
      this.busy.set(true);
      this.error.set('');
      this.api
        .commit({ ...payload, requestKey: this.requestKey })
        .pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (saved) => {
            this.view.update((v) => (v ? { ...v, expectedRevision: saved.revision } : v));
            this.message.set(
              `Đã chốt ${saved.tickets.length} vé cho kỳ ${saved.targetDate}, bản ${saved.revision}. Chỉnh tiếp sẽ tạo bản mới trước cutoff.`,
            );
            this.newRequest();
            this.saveDraft();
            this.busy.set(false);
            this.loadHistory();
          },
          error: (e) => {
            this.error.set(this.errorText(e));
            this.busy.set(false);
            if (e.status === 400 || e.status === 409) {
              this.newRequest();
              this.saveDraft();
            }
          },
        });
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }
  loadHistory() {
    const view = this.view();
    if (!view) return;
    this.historyCancel.next();
    this.historyError.set('');
    this.api
      .history(view.category)
      .pipe(takeUntil(this.historyCancel), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.batches.set(data.batches);
          const revision = Math.max(
            0,
            ...data.batches.filter((b) => b.targetDate === view.targetDate).map((b) => b.revision),
          );
          // Do not silently resolve a conflicting in-flight commit.
          if (!this.busy() && !this.requestPayload) {
            this.view.update((v) => (v ? { ...v, expectedRevision: revision } : v));
            this.saveDraft();
          }
        },
        error: () =>
          this.historyError.set(
            'Chưa tải được lịch sử vé người dùng. Bấm tải lại trước khi chốt nếu đã dùng tab khác.',
          ),
      });
  }
  reconcile() {
    if (this.busy()) return;
    this.busy.set(true);
    this.api
      .reconcile()
      .pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.loadHistory();
        },
        error: (e) => {
          this.error.set(this.errorText(e));
          this.busy.set(false);
        },
      });
  }
  private errorText(error: { error?: { message?: string; detail?: string }; status?: number }) {
    return (
      error.error?.message ||
      error.error?.detail ||
      (error.status === 409
        ? 'Nguồn hoặc bản chốt đã thay đổi. Tải lại dữ liệu; vé nháp vẫn được giữ.'
        : 'Chưa hoàn tất thao tác. Bạn có thể thử lại với cùng danh sách vé.')
    );
  }
}
