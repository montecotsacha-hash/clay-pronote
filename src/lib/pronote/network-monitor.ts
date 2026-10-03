import { useEffect, useState } from "react";

/**
 * NetworkMonitor — the web equivalent of Android's ConnectivityManager.
 * The UI subscribes to it to switch between `success` and `offline`.
 */
export function useNetworkMonitor(): { online: boolean } {
  const [online, setOnline] = useState(
    typeof navigator === "undefined" ? true : navigator.onLine,
  );

  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener("online", up);
    window.addEventListener("offline", down);
    return () => {
      window.removeEventListener("online", up);
      window.removeEventListener("offline", down);
    };
  }, []);

  return { online };
}
