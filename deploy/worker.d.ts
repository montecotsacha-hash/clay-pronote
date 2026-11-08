/**
 * Types du Worker du site, importé par les tests vitest.
 *
 * Le module réel est `worker.js`, exécuté par le runtime Cloudflare Workers :
 * on ne décrit ici que le handler par défaut, pour que `tsc -b` (le build du
 * site) accepte `import worker from "../deploy/worker.js"`.
 */
declare const worker: {
  fetch(request: Request, env: Record<string, unknown>): Promise<Response>;
};

export default worker;
