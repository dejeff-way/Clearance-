/* Clearance — service worker
   Goal: if the tab reloads or gets evicted while offline, the app still opens
   and restores the saved route. Never caches HERE API calls (routes stay live). */

const VERSION = "clearance-v1";
const SHELL_CACHE = "clr-shell-" + VERSION;
const TILE_CACHE = "clr-tiles-" + VERSION;
const ASSET_CACHE = "clr-assets-" + VERSION;
const MAX_TILES = 1200; // roughly a metro area's worth at driving zooms

const SHELL = ["./", "./index.html"];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(SHELL_CACHE)
      .then((c) => Promise.allSettled(SHELL.map((u) => c.add(u))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.filter((k) => k.startsWith("clr-") && !k.includes(VERSION))
            .map((k) => caches.delete(k))
      )
    ).then(() => self.clients.claim())
  );
});

async function trimCache(name, max) {
  const c = await caches.open(name);
  const keys = await c.keys();
  if (keys.length <= max) return;
  // delete oldest first (keys are in insertion order)
  const excess = keys.length - max;
  for (let i = 0; i < excess; i++) await c.delete(keys[i]);
}

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  // HERE API — always live, never cached. Offline = fails, app handles it.
  if (url.hostname.endsWith("hereapi.com")) return;

  // The app itself (navigations): network-first so deploys update,
  // cached copy when offline.
  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(SHELL_CACHE).then((c) => c.put("./", copy));
          return res;
        })
        .catch(() =>
          caches.match("./").then((hit) => hit || caches.match("./index.html"))
        )
    );
    return;
  }

  // Map tiles: cache-first — tiles you've seen stay viewable offline.
  if (url.hostname.endsWith("basemaps.cartocdn.com")) {
    e.respondWith(
      caches.match(req).then((hit) => {
        if (hit) return hit;
        return fetch(req).then((res) => {
          const copy = res.clone();
          caches.open(TILE_CACHE).then((c) => {
            c.put(req, copy);
            trimCache(TILE_CACHE, MAX_TILES);
          });
          return res;
        });
      })
    );
    return;
  }

  // Static assets (Leaflet, fonts): cache-first, they never change per version.
  if (
    url.hostname === "cdnjs.cloudflare.com" ||
    url.hostname === "fonts.googleapis.com" ||
    url.hostname === "fonts.gstatic.com"
  ) {
    e.respondWith(
      caches.match(req).then((hit) => {
        if (hit) return hit;
        return fetch(req).then((res) => {
          const copy = res.clone();
          caches.open(ASSET_CACHE).then((c) => c.put(req, copy));
          return res;
        });
      })
    );
  }
});
