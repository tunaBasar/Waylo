/** id -> name map from organization lists; the label falls back to a short id when the name is unknown. */
export function orgNameMap(lists: Array<Array<{ id: string; name: string }>>): Record<string, string> {
  const map: Record<string, string> = {};
  for (const list of lists) for (const o of list) map[o.id] = o.name;
  return map;
}

export function orgLabel(map: Record<string, string>, id: string): string {
  return map[id] ?? `${id.slice(0, 8)}...`;
}
