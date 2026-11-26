'use client';

import { useEffect, useState } from 'react';
import { usePartner } from '@/lib/partner-context';
import { ROLE_LABEL, partnerErrorMessage, partnerApi, teamInviteErrorMessage, type PartnerRole, type TeamMember } from '@/lib/partner';
import { formatDate } from '@/lib/utils';
import { useSingleFlight } from '@/lib/use-single-flight';
import { Button, Card, EmptyState, Field, inputClass, LoadingRows, PageHeader } from '@/components/partner/ui';

const GRANTABLE: PartnerRole[] = ['HOTEL_MANAGER', 'HOTEL_STAFF', 'HOTEL_OWNER'];

export default function PartnerTeamPage() {
  const { organizationId, hotels, hotelId, isCarRentalOrg, isBusOrg, loading: workspaceLoading } = usePartner();
  const flight = useSingleFlight();
  const [reloads, setReloads] = useState(0);
  const key = `${organizationId}|${reloads}`;
  const [result, setResult] = useState<{ key: string; members: TeamMember[]; error: string | null } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<PartnerRole>('HOTEL_STAFF');
  const [inviteHotel, setInviteHotel] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!organizationId) return;
    let alive = true;
    partnerApi.members(organizationId)
      .then(res => { if (alive) setResult({ key, members: res.data ?? [], error: null }); })
      .catch(e => { if (alive) setResult({ key, members: [], error: partnerErrorMessage(e, 'Ekip yuklenemedi') }); });
    return () => { alive = false; };
  }, [organizationId, key]);

  const load = async () => setReloads(n => n + 1);
  const loading = result?.key !== key && !result;
  const members = result?.members ?? [];
  const error = result?.error ?? null;

  const canInvite = !isCarRentalOrg && !isBusOrg;
  const targetHotel = inviteHotel || hotelId || hotels[0]?.id || '';

  async function invite(e: React.FormEvent) {
    e.preventDefault();
    if (!organizationId || !targetHotel) return;
    await flight(`team-invite:${organizationId}`, async () => {
      setBusy(true);
      setNotice(null);
      try {
        await partnerApi.grantRole(organizationId, { email: email.trim(), role, hotelId: targetHotel });
        setEmail('');
        setNotice('Rol verildi');
        await load();
      } catch (err) {
        setNotice(teamInviteErrorMessage(err));
      } finally {
        setBusy(false);
      }
    });
  }

  async function remove(m: TeamMember) {
    if (!organizationId || !window.confirm(`${m.email} icin ${ROLE_LABEL[m.role]} rolu kaldirilsin mi?`)) return;
    await flight(`team-remove:${m.grantId}`, async () => {
      setBusy(true);
      setNotice(null);
      try {
        await partnerApi.revokeRole(organizationId, m.grantId);
        setNotice('Rol kaldirildi');
        await load();
      } catch (err) {
        setNotice(partnerErrorMessage(err, 'Rol kaldirilamadi'));
      } finally {
        setBusy(false);
      }
    });
  }

  if (workspaceLoading || loading) return <LoadingRows />;
  if (error) return <EmptyState icon="error" title="Yuklenemedi" description={error} />;

  return (
    <div className="space-y-space-lg">
      <PageHeader title="Ekip" subtitle="Isletmenize erisimi olan kisiler" />
      {notice && <div className="rounded-xl bg-surface-container px-4 py-3 text-sm" role="status">{notice}</div>}

      {canInvite && (
        <Card className="p-4">
          <form onSubmit={invite} className="grid gap-3 sm:grid-cols-4 items-end">
            <Field label="E-posta">
              <input type="email" required className={inputClass} value={email} onChange={e => setEmail(e.target.value)} />
            </Field>
            <Field label="Rol">
              <select className={inputClass} value={role} onChange={e => setRole(e.target.value as PartnerRole)}>
                {GRANTABLE.map(r => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
              </select>
            </Field>
            <Field label="Otel">
              <select className={inputClass} value={targetHotel} onChange={e => setInviteHotel(e.target.value)}>
                {hotels.map(h => <option key={h.id} value={h.id}>{h.name}</option>)}
              </select>
            </Field>
            <Button type="submit" disabled={busy || !targetHotel || !email.trim()}>Rol ver</Button>
          </form>
          <p className="mt-2 text-xs text-on-surface-variant">Kisinin once Waylo&apos;ya kayit olmasi gerekir.</p>
        </Card>
      )}

      {members.length === 0 ? (
        <EmptyState icon="group" title="Ekip uyesi yok" description="Bu isletmede henuz rol atanmis kimse yok." />
      ) : (
        <div className="space-y-3">
          {members.map(m => (
            <Card key={m.grantId} className="p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-on-surface">{m.name || m.email}</p>
                  <p className="text-sm text-on-surface-variant">{m.email}</p>
                  <p className="text-xs text-outline">{ROLE_LABEL[m.role]} · {formatDate(m.createdAt)}</p>
                </div>
                {m.role !== 'ORGANIZATION_OWNER' && (
                  <Button variant="danger" disabled={busy} onClick={() => void remove(m)}>Kaldir</Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
