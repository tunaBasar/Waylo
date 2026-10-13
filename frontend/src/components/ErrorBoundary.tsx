'use client';

import { Component, ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div className="flex flex-col items-center justify-center py-16 px-4">
          <div className="w-16 h-16 bg-error-container rounded-full flex items-center justify-center mb-4">
            <span className="material-symbols-outlined text-error text-3xl">warning</span>
          </div>
          <h2 className="text-xl font-semibold text-on-surface mb-2">Bir Hata Olustu</h2>
          <p className="text-sm text-on-surface-variant mb-6 text-center max-w-md">
            {this.state.error?.message || 'Beklenmeyen bir hata olustu. Lutfen tekrar deneyin.'}
          </p>
          <button
            onClick={() => this.setState({ hasError: false, error: null })}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-primary text-on-primary rounded-xl text-sm font-medium shadow-md hover:shadow-lg active:scale-[0.98] transition-all"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span> Tekrar Dene
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
