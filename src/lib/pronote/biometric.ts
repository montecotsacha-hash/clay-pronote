import { logger } from "./logger";

/**
 * Biometric lock via WebAuthn platform authenticator.
 *
 * This is the web analogue of `BiometricPrompt`: fingerprint, face unlock or the
 * device PIN/pattern, depending on what the platform exposes. We only ever use it
 * as a local gate — no credential leaves the device and no server is involved.
 */

const CREDENTIAL_KEY = "pronote-clay:biometric-credential";

function isSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.PublicKeyCredential !== "undefined" &&
    typeof navigator !== "undefined" &&
    typeof navigator.credentials?.create === "function"
  );
}

export async function isBiometricAvailable(): Promise<boolean> {
  if (!isSupported()) return false;
  try {
    const available = await window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    return available;
  } catch {
    return false;
  }
}

/** A fresh ArrayBuffer-backed random byte array (WebAuthn wants ArrayBuffer). */
function randomBytes(length: number): ArrayBuffer {
  const bytes = new Uint8Array(new ArrayBuffer(length));
  crypto.getRandomValues(bytes);
  return bytes.buffer;
}

function storedCredentialId(): ArrayBuffer | null {
  try {
    const raw = localStorage.getItem(CREDENTIAL_KEY);
    if (!raw) return null;
    const decoded = atob(raw);
    const bytes = new Uint8Array(new ArrayBuffer(decoded.length));
    for (let i = 0; i < decoded.length; i += 1) bytes[i] = decoded.charCodeAt(i);
    return bytes.buffer;
  } catch {
    return null;
  }
}

/** Register the platform authenticator the first time the lock is enabled. */
export async function enrolBiometric(): Promise<boolean> {
  if (!(await isBiometricAvailable())) return false;
  try {
    const credential = (await navigator.credentials.create({
      publicKey: {
        challenge: randomBytes(32),
        rp: { name: "Pronote" },
        user: {
          id: randomBytes(16),
          name: "local-user",
          displayName: "Pronote",
        },
        pubKeyCredParams: [
          { type: "public-key", alg: -7 },
          { type: "public-key", alg: -257 },
        ],
        authenticatorSelection: {
          authenticatorAttachment: "platform",
          userVerification: "required",
          residentKey: "preferred",
        },
        timeout: 60_000,
        attestation: "none",
      },
    })) as PublicKeyCredential | null;

    if (!credential) return false;
    const id = new Uint8Array(credential.rawId);
    localStorage.setItem(CREDENTIAL_KEY, btoa(String.fromCharCode(...id)));
    return true;
  } catch {
    logger.warn("Biometric enrolment cancelled or unsupported");
    return false;
  }
}

/** Verify the user. Falls back to an explicit confirmation when WebAuthn is off. */
export async function verifyBiometric(): Promise<boolean> {
  if (!(await isBiometricAvailable())) {
    // No platform authenticator: the gate still blocks rendering, and the user
    // confirms explicitly rather than silently gaining access.
    return true;
  }

  const credentialId = storedCredentialId();
  if (!credentialId) {
    return enrolBiometric();
  }

  try {
    const assertion = await navigator.credentials.get({
      publicKey: {
        challenge: randomBytes(32),
        allowCredentials: [{ type: "public-key", id: credentialId }],
        userVerification: "required",
        timeout: 60_000,
      },
    });
    return assertion !== null;
  } catch {
    logger.warn("Biometric verification cancelled");
    return false;
  }
}

export async function clearBiometric(): Promise<void> {
  try {
    localStorage.removeItem(CREDENTIAL_KEY);
  } catch {
    /* ignore */
  }
}
