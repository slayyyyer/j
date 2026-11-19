/**
 * Static hosting: every request is served from the Vite build in ./dist.
 * SPA fallback is declared in site-manifest.json.
 */
export default {
  fetch(request, env) {
    return env.ASSETS.fetch(request);
  },
};
