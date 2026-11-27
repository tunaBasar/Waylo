import type { Membership } from './partner';

type NavLike = { href: string };

const MANAGE_ONLY_HREFS = ['/partner/room-types', '/partner/rate-plans', '/partner/calendar', '/partner/automation'];
const TEAM_HREF = '/partner/team';

/** HOTEL_MANAGE / ROOM_MANAGE holders: organization owner, or owner/manager granted on this very hotel. */
export function canManageHotel(membership: Membership | null | undefined, hotelId: string | null | undefined): boolean {
  if (!membership) return false;
  return membership.roles.some(r =>
    (r.role === 'ORGANIZATION_OWNER' && r.scopeType === 'ORGANIZATION') ||
    ((r.role === 'HOTEL_OWNER' || r.role === 'HOTEL_MANAGER') && r.scopeType === 'HOTEL' && !!hotelId && r.scopeId === hotelId));
}

/** auth-service MEMBER_VIEW_ROLES: organization owner, hotel owner, hotel manager (any scope). */
export function canViewTeam(membership: Membership | null | undefined): boolean {
  if (!membership) return false;
  return membership.roles.some(r => r.role === 'ORGANIZATION_OWNER' || r.role === 'HOTEL_OWNER' || r.role === 'HOTEL_MANAGER');
}

/** Hotel sidebar without the entries the user's role would only get a 403 for. Null membership (loading) keeps everything. */
export function hotelNavFor<T extends NavLike>(membership: Membership | null | undefined, hotelId: string | null | undefined, fullNav: T[]): T[] {
  if (!membership) return fullNav;
  const manage = canManageHotel(membership, hotelId);
  const team = canViewTeam(membership);
  return fullNav.filter(item => {
    if (MANAGE_ONLY_HREFS.includes(item.href)) return manage;
    if (item.href === TEAM_HREF) return team;
    return true;
  });
}
