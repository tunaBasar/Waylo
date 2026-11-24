'use client';

import { useState } from 'react';
import { Button, Card, StatusPill, inputClass } from '@/components/partner/ui';
import {
  BODY_MAX, ROLE_LABEL, STATUS_LABEL, STATUS_TONE, canClose, canReply, validateMessage,
  type SupportCase, type SupportRole,
} from '@/lib/support';

const fmt = (iso: string) => new Date(iso).toLocaleString('tr-TR', { dateStyle: 'short', timeStyle: 'short' });

export function SupportStatusPill({ status }: { status: SupportCase['status'] }) {
  return <StatusPill tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</StatusPill>;
}

/** Thread view with reply box and optional close. Case must carry messages (detail response). */
export function SupportThread({ detail, role, onReply, onClose }: {
  detail: SupportCase;
  role: SupportRole;
  onReply: (body: string) => Promise<void>;
  onClose?: () => Promise<void>;
}) {
  const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (fn: () => Promise<void>, after?: () => void) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      after?.();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'İşlem başarısız');
    } finally {
      setBusy(false);
    }
  };

  const send = () => {
    const invalid = validateMessage(body);
    if (invalid) { setError(invalid); return; }
    void run(() => onReply(body.trim()), () => setBody(''));
  };

  return (
    <Card className="p-4 space-y-3">
      <div className="flex items-center justify-between gap-2" data-testid="support-thread">
        <h3 className="text-sm font-semibold text-on-surface" data-testid="support-thread-subject">{detail.subject}</h3>
        <SupportStatusPill status={detail.status} />
      </div>
      <ul className="space-y-2">
        {(detail.messages ?? []).map(m => (
          <li key={m.id} data-testid="support-message"
            className={`rounded-lg p-3 text-sm ${m.authorRole === role ? 'bg-primary-container text-on-primary-container' : 'bg-surface-container text-on-surface'}`}>
            <p className="text-[11px] font-semibold opacity-70">{ROLE_LABEL[m.authorRole]} · {fmt(m.createdAt)}</p>
            <p className="whitespace-pre-wrap">{m.body}</p>
          </li>
        ))}
      </ul>
      {canReply(detail.status, role) ? (
        <div className="space-y-2">
          <textarea className={inputClass} rows={3} maxLength={BODY_MAX} value={body} data-testid="support-reply-input"
            onChange={e => setBody(e.target.value)} placeholder="Yanıtınız" />
          <div className="flex gap-2">
            <Button disabled={busy} onClick={send} data-testid="support-reply-send">Gönder</Button>
            {onClose && canClose(detail.status, role) && (
              <Button variant="secondary" disabled={busy} data-testid="support-close"
                onClick={() => void run(onClose)}>Talebi kapat</Button>
            )}
          </div>
        </div>
      ) : (
        <p className="text-xs text-on-surface-variant" data-testid="support-closed-note">Bu talep kapatıldı, yeni mesaj gönderilemez.</p>
      )}
      {error && <p className="text-xs text-error" data-testid="support-error">{error}</p>}
    </Card>
  );
}
