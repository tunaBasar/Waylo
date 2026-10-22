'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { partnerApi, type Order, type OrderStatus } from '@/lib/partner';
import { formatCurrency, formatDate } from '@/lib/utils';
import { Button, Card, EmptyState, LoadingRows, PageHeader, StatusPill } from '@/components/partner/ui';

const ORDER_STATUSES: Array<{ key: 'ALL' | OrderStatus; label: string }> = [
  { key: 'ALL', label: 'Tumu' },
  { key: 'PENDING', label: 'Bekliyor' },
  { key: 'CONFIRMED', label: 'Onaylandi' },
  { key: 'CANCELLED', label: 'Iptal' },
  { key: 'FAILED', label: 'Basarisiz' },
];

const STATUS_TONE: Record<OrderStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  CONFIRMED: 'success',
  PENDING: 'warning',
  CANCELLED: 'neutral',
  FAILED: 'error',
};

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Bekliyor',
  CONFIRMED: 'Onaylandi',
  CANCELLED: 'Iptal',
  FAILED: 'Basarisiz',
};

const VERTICAL_LABEL: Record<string, string> = { HOTEL: 'Otel', FLIGHT: 'Ucus', CAR: 'Arac' };

function StatusChip({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-medium transition-colors ${
        active
          ? 'bg-primary text-on-primary'
          : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container'
      }`}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | OrderStatus>('ALL');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await partnerApi.myOrders();
      setOrders(res.data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Yuklenemedi');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) return load(); });
    return () => { cancelled = true; };
  }, [load]);

  const filteredOrders = useMemo(() => {
    const base = filter === 'ALL' ? orders : orders.filter(o => o.status === filter);
    return [...base].sort((a, b) => {
      const ta = a.createdAt ?? '';
      const tb = b.createdAt ?? '';
      return tb.localeCompare(ta);
    });
  }, [orders, filter]);

  if (loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader
        title="Siparisler"
        subtitle="Kendi hesabinizla verdiginiz siparisler. Otelinize gelen siparisler ileride burada listelenecek."
      />
      <div className="flex flex-wrap gap-1.5">
        {ORDER_STATUSES.map(s => (
          <StatusChip
            key={s.key}
            active={filter === s.key}
            label={s.label}
            onClick={() => setFilter(s.key)}
          />
        ))}
      </div>
      {filteredOrders.length === 0 ? (
        <EmptyState
          icon="receipt_long"
          title={filter === 'ALL' ? 'Henuz siparis yok' : 'Bu durumda siparis yok'}
        />
      ) : (
        <div className="space-y-2">
          {filteredOrders.map(order => {
            const isExpanded = expandedId === order.id;
            return (
              <Card key={order.id} className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold text-on-surface truncate">
                        #{order.id.slice(0, 8)}
                      </p>
                      <StatusPill tone={STATUS_TONE[order.status]}>
                        {STATUS_LABEL[order.status] ?? order.status}
                      </StatusPill>
                    </div>
                    <div className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-sm text-on-surface-variant">
                      <span>{formatCurrency(order.totalAmount, order.currency)}</span>
                      <span>{formatDate(order.createdAt)}</span>
                      <span>{order.items.length} urun</span>
                    </div>
                  </div>
                  <Button
                    variant="ghost"
                    onClick={() => setExpandedId(isExpanded ? null : order.id)}
                  >
                    Detay
                  </Button>
                </div>
                {isExpanded && order.items.length > 0 && (
                  <div className="mt-3 border-t border-outline-variant/40 pt-3 space-y-2">
                    {order.items.map(item => (
                      <div key={item.id} className="flex justify-between text-sm">
                        <span className="text-on-surface-variant truncate pr-4">
                          {item.description || item.productRef}
                          <span className="text-outline ml-1">
                            ({VERTICAL_LABEL[item.vertical] ?? item.vertical}) {item.quantity} x {formatCurrency(item.unitAmount, item.currency)}
                          </span>
                        </span>
                        <span className="text-on-surface font-medium tabular-nums shrink-0 ml-4">
                          {formatCurrency(item.subtotal, item.currency)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
