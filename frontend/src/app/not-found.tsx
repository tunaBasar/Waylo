import Link from 'next/link';
import { Plane, Home } from 'lucide-react';

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 via-white to-emerald-50 px-4">
      <div className="text-center">
        <div className="inline-flex items-center gap-2 mb-6">
          <div className="bg-blue-600 p-2.5 rounded-xl">
            <Plane className="text-white" size={28} />
          </div>
          <span className="text-3xl font-bold text-gray-900">Waylo</span>
        </div>

        <h1 className="text-8xl font-bold text-gray-200 mb-4">404</h1>
        <h2 className="text-2xl font-semibold text-gray-900 mb-2">Sayfa Bulunamadi</h2>
        <p className="text-gray-500 mb-8 max-w-md mx-auto">
          Aradiginiz sayfa mevcut degil veya tasindi. Ana sayfaya donerek devam edebilirsiniz.
        </p>

        <Link href="/"
          className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium">
          <Home size={18} /> Ana Sayfaya Don
        </Link>
      </div>
    </div>
  );
}
