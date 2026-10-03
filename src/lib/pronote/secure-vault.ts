import { logger } from "./logger";

/**
 * SecureCredentialsManager — the browser analogue of Android Keystore.
 *
 *   credentials -> AES-GCM -> ciphertext in localStorage (scoped per account)
 *   AES key     -> non-extractable CryptoKey stored in IndexedDB
 *
 * The key can be used but never read back by JavaScript, which is the closest
 * WebCrypto gets to a Keystore-backed key. Plaintext credentials are never
 * written to disk, never sent anywhere, and never logged.
 *
 * When the vault is unavailable (private mode, no IndexedDB) we degrade to
 * in-memory-only storage: the session is lost on reload rather than written in
 * the clear.
 */

const DB_NAME = "pronote-clay-vault";
const KEY_ID = "aes-gcm-key";
const STORE = "keys";

export interface StoredCredentials {
  schoolUrl: string;
  schoolName: string;
  username: string;
  /** Only kept when the connector cannot issue a durable token. */
  password?: string;
  /** Preferred over `password` when the connector supports it. */
  token?: string;
  expiresAt?: number;
}

interface VaultRecord {
  iv: number[];
  data: number[];
}

const enc = new TextEncoder();
const dec = new TextDecoder();

let memoryFallback: string | null = null;

function hasIndexedDb(): boolean {
  try {
    return typeof indexedDB !== "undefined";
  } catch {
    return false;
  }
}

function hasCrypto(): boolean {
  return typeof crypto !== "undefined" && typeof crypto.subtle !== "undefined";
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function getKey(): Promise<CryptoKey | null> {
  if (!hasIndexedDb() || !hasCrypto()) return null;
  try {
    const db = await openDb();
    const existing = await new Promise<CryptoKey | undefined>((resolve, reject) => {
      const tx = db.transaction(STORE, "readonly");
      const req = tx.objectStore(STORE).get(KEY_ID);
      req.onsuccess = () => resolve(req.result as CryptoKey | undefined);
      req.onerror = () => reject(req.error);
    });
    if (existing) return existing;

    const key = await crypto.subtle.generateKey(
      { name: "AES-GCM", length: 256 },
      /* extractable */ false,
      ["encrypt", "decrypt"],
    );
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(key, KEY_ID);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    return key;
  } catch {
    return null;
  }
}

function storageKey(accountId: string) {
  return `pronote-clay:vault:${accountId}`;
}

export const SecureCredentialsManager = {
  /** Encrypt and persist the secrets for one account. */
  async save(accountId: string, credentials: StoredCredentials): Promise<void> {
    const json = JSON.stringify(credentials);
    const key = await getKey();

    if (!key) {
      memoryFallback = json;
      logger.warn("Vault unavailable — credentials kept in memory only");
      return;
    }

    const iv = crypto.getRandomValues(new Uint8Array(12));
    const cipher = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv },
      key,
      enc.encode(json),
    );
    const record: VaultRecord = {
      iv: Array.from(iv),
      data: Array.from(new Uint8Array(cipher)),
    };
    try {
      localStorage.setItem(storageKey(accountId), JSON.stringify(record));
    } catch {
      memoryFallback = json;
      logger.warn("Vault write failed — credentials kept in memory only");
    }
  },

  /** Decrypt the secrets for one account. Returns null when none are stored. */
  async load(accountId: string): Promise<StoredCredentials | null> {
    if (memoryFallback) {
      try {
        return JSON.parse(memoryFallback) as StoredCredentials;
      } catch {
        return null;
      }
    }

    const key = await getKey();
    if (!key) return null;

    let raw: string | null = null;
    try {
      raw = localStorage.getItem(storageKey(accountId));
    } catch {
      return null;
    }
    if (!raw) return null;

    try {
      const record = JSON.parse(raw) as VaultRecord;
      const iv = new Uint8Array(record.iv);
      const data = new Uint8Array(record.data);
      const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, data);
      return JSON.parse(dec.decode(plain)) as StoredCredentials;
    } catch {
      logger.warn("Vault read failed — treating as no stored credentials");
      return null;
    }
  },

  async clear(accountId: string): Promise<void> {
    memoryFallback = null;
    try {
      localStorage.removeItem(storageKey(accountId));
    } catch {
      /* ignore */
    }
  },

  /** Key rotation: drop the AES key so the next save re-encrypts with a new one. */
  async rotateKey(): Promise<void> {
    memoryFallback = null;
    if (!hasIndexedDb()) return;
    try {
      const db = await openDb();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction(STORE, "readwrite");
        tx.objectStore(STORE).delete(KEY_ID);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
    } catch {
      /* ignore */
    }
  },
};
