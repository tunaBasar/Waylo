import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface relative overflow-hidden px-4">
      <div className="absolute -top-32 -left-32 w-80 h-80 rounded-full bg-primary/8 blur-3xl" />
      <div className="absolute -bottom-20 -right-20 w-64 h-64 rounded-full bg-error/10 blur-3xl" />

      <div className="text-center relative z-10">
        <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-primary shadow-lg mb-6">
          <span className="material-symbols-outlined text-on-primary text-2xl">flight</span>
        </div>

        <h1 className="text-8xl font-bold text-outline/20 mb-4">404</h1>
        <h2 className="text-2xl font-semibold text-on-surface mb-2">Sayfa Bulunamadi</h2>
        <p className="text-on-surface-variant text-sm mb-8 max-w-md mx-auto">
          Aradiginiz sayfa mevcut degil veya tasindi. Ana sayfaya donerek devam edebilirsiniz.
        </p>

        <Link href="/"
          className="inline-flex items-center gap-2 px-6 py-3 bg-primary text-on-primary rounded-xl font-medium shadow-md hover:shadow-lg active:scale-[0.98] transition-all text-sm">
          <span className="material-symbols-outlined text-[18px]">home</span> Ana Sayfaya Don
        </Link>
      </div>
    </div>
  );
}
