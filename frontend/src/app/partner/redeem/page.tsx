'use client';

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { api, ApiError } from '@/lib/api';
import { usePartner } from '@/lib/partner-context';
import { parseVoucherPayload, redeemErrorMessage } from '@/lib/voucher';

interface RedeemResult {
  voucherId: string;
  orderItemId: string;
  description: string;
  amountReleased: number | null;
  currency: string;
  redeemedAt: string;
}

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}

function createDetector(): BarcodeDetectorLike | null {
  const Ctor = (globalThis as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => BarcodeDetectorLike }).BarcodeDetector;
  return Ctor ? new Ctor({ formats: ['qr_code'] }) : null;
}

function detectCamera(): boolean {
  return createDetector() !== null && !!navigator.mediaDevices?.getUserMedia;
}

export default function RedeemPage() {
  const { organizationId } = usePartner();
  const [manual, setManual] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<RedeemResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [scanning, setScanning] = useState(false);
  const cameraSupported = useSyncExternalStore(() => () => {}, detectCamera, () => false);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const busyRef = useRef(false);

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach(t => t.stop());
    streamRef.current = null;
    setScanning(false);
  }, []);

  useEffect(() => stopCamera, [stopCamera]);

  const redeem = useCallback(async (raw: string) => {
    if (busyRef.current) return;
    setResult(null);
    if (!organizationId) {
      setError('Once bir isletme secin.');
      return;
    }
    if (!parseVoucherPayload(raw)) {
      setError(redeemErrorMessage('INVALID_VOUCHER'));
      return;
    }
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const res = await api.post<RedeemResult>(
        `/api/partner/organizations/${encodeURIComponent(organizationId)}/vouchers/redeem`,
        { payload: raw.trim() },
      );
      setResult(res.data);
      setManual('');
    } catch (err) {
      if (err instanceof ApiError) setError(redeemErrorMessage(err.code, err.message));
      else setError(redeemErrorMessage(null));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, [organizationId]);

  const startCamera = useCallback(async () => {
    const detector = createDetector();
    if (!detector) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      streamRef.current = stream;
      setScanning(true);
      const video = videoRef.current;
      if (!video) return;
      video.srcObject = stream;
      await video.play();
      const tick = async () => {
        if (!streamRef.current) return;
        try {
          const codes = await detector.detect(video);
          const hit = codes.map(c => c.rawValue).find(v => parseVoucherPayload(v));
          if (hit) {
            stopCamera();
            await redeem(hit);
            return;
          }
        } catch {
          // frame not ready, keep scanning
        }
        setTimeout(tick, 250);
      };
      void tick();
    } catch {
      setError('Kameraya erisilemedi. Kodu asagiya yapistirabilirsiniz.');
      stopCamera();
    }
  }, [redeem, stopCamera]);

  return (
    <div className="space-y-space-md max-w-xl">
      <h2 className="text-xl font-semibold text-on-surface tracking-tight">QR Okut</h2>

      {cameraSupported ? (
        <div className="space-y-2">
          <video ref={videoRef} className={scanning ? 'w-full rounded-xl bg-black' : 'hidden'} muted playsInline />
          {scanning ? (
            <button onClick={stopCamera} className="rounded-xl bg-surface-container px-4 py-2 text-sm font-semibold">Kamerayi kapat</button>
          ) : (
            <button onClick={() => void startCamera()} disabled={busy || !organizationId}
              className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-on-primary disabled:opacity-50">Kamerayla okut</button>
          )}
        </div>
      ) : (
        <p className="text-xs text-on-surface-variant">Bu tarayici kamera ile QR okumayi desteklemiyor, kodu yapistirin.</p>
      )}

      <form className="space-y-2" onSubmit={e => { e.preventDefault(); void redeem(manual); }}>
        <label className="block space-y-1">
          <span className="text-[11px] font-medium text-on-surface-variant">Kupon kodu (WAYLO1:...)</span>
          <input value={manual} onChange={e => setManual(e.target.value)} autoComplete="off" spellCheck={false}
            className="w-full rounded-xl bg-surface-container-lowest px-3 py-2 text-sm font-mono" />
        </label>
        <button type="submit" disabled={busy || !manual.trim() || !organizationId}
          className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-on-primary disabled:opacity-50">
          {busy ? 'Kontrol ediliyor...' : 'Kuponu kullan'}
        </button>
      </form>

      {error && (
        <p role="alert" className="rounded-xl bg-error-container p-3 text-sm text-on-error-container">{error}</p>
      )}
      {result && (
        <div role="status" className="rounded-xl bg-tertiary-container p-4 text-on-tertiary-container space-y-1">
          <p className="font-semibold">Kupon kullanildi</p>
          <p className="text-sm">{result.description}</p>
          {result.amountReleased != null && (
            <p className="text-lg font-bold">
              {new Intl.NumberFormat('tr-TR', { style: 'currency', currency: result.currency }).format(result.amountReleased)} isletmeye aktarildi
            </p>
          )}
          <p className="text-xs">{new Date(result.redeemedAt).toLocaleString('tr-TR')}</p>
        </div>
      )}
    </div>
  );
}
