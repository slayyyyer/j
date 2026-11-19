import { defineConfig } from 'vite';

/**
 * The Freebuff deployment serves the built site from the domain root, while a
 * GitHub project page serves it from `/<repo>/`. The Pages workflow sets
 * SITE_BASE, so the default stays root-relative for local development.
 */
export default defineConfig({
  base: process.env.SITE_BASE ?? '/',
});
