/** Customer-facing cancellation texts (CANCELLATION-CONTRACT.md). */
export function policyText(policy: string | null | undefined, hours: number | null | undefined): string {
  if (policy === 'NON_REFUNDABLE') return 'Iade edilemez';
  if (policy === 'FREE_UNTIL' && hours != null) return `Baslangica ${hours} saat kalaya kadar ucretsiz iptal`;
  return 'Ucretsiz iptal';
}

export function refundText(amount: number, currency: string): string {
  if (!(amount > 0)) return 'Iade yapilmayacak';
  const formatted = new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(amount);
  return `${formatted} iade edilecek`;
}
