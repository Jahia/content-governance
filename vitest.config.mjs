import {fileURLToPath} from 'node:url';
import {defineConfig} from 'vitest/config';

// Unit tests of the front-end code. The Cypress suite in tests/ runs against a live Jahia instead.
export default defineConfig({
    resolve: {
        // The host's library, shared and never installed (webpack.config.js)
        alias: {'@apollo/client': fileURLToPath(new URL('src/test/javascript/stubs/apollo-client.js', import.meta.url))}
    },
    test: {
        include: ['src/test/javascript/**/*.test.js']
    }
});
