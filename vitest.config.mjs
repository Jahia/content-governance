import {defineConfig} from 'vitest/config';

// Unit tests of the front-end code. The Cypress suite in tests/ runs against a live Jahia instead.
export default defineConfig({
    test: {
        include: ['src/test/javascript/**/*.test.js']
    }
});
