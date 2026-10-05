import { Component, type ErrorInfo, type ReactNode } from "react";

type Props = { children: ReactNode };
type State = { error: Error | null };

/**
 * ErrorBoundary — a render crash must never leave a blank page.
 * The preview is the only place the APK is downloadable, so we always show a
 * readable message and a way out instead of an empty screen.
 */
export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Pronote — erreur de rendu", error, info);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-4 px-6 text-center">
        <h1 className="text-2xl font-black">L'application n'a pas pu démarrer</h1>
        <p className="text-[#6b5f87]">
          Une erreur est survenue pendant le chargement de la page. Recharge
          pour réessayer.
        </p>
        <pre className="clay-card overflow-x-auto p-4 text-left text-xs text-[#6b5f87]">
          {error.message}
        </pre>
        <button className="clay-btn" onClick={() => window.location.reload()}>
          Recharger la page
        </button>
      </main>
    );
  }
}