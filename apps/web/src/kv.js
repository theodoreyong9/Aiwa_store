// A tiny key-value cache in IndexedDB (the last index, the packages already opened). Never required: if the browser
// refuses IndexedDB, it is a memory map and the store works the same, minus offline.

export function openCache(name = 'aiwa-store-cache') {
  const memory = new Map();
  const db = typeof indexedDB === 'undefined' ? null : new Promise((resolve) => {
    try {
      const request = indexedDB.open(name, 1);
      request.onupgradeneeded = () => request.result.createObjectStore('kv');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => resolve(null);
    } catch { resolve(null); }
  });
  const run = async (mode, fn) => {
    const handle = db ? await db : null;
    if (!handle) return fn(null)?.result;
    return new Promise((resolve, reject) => {
      const tx = handle.transaction('kv', mode);
      const result = fn(tx.objectStore('kv'));
      tx.oncomplete = () => resolve(result?.result);
      tx.onerror = () => reject(tx.error);
    });
  };
  return {
    async get(key) {
      return run('readonly', (store) => (store ? store.get(key) : { result: memory.get(key) }));
    },
    async set(key, value) {
      return run('readwrite', (store) => { if (store) store.put(value, key); else memory.set(key, value); return { result: undefined }; });
    },
  };
}
