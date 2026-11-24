import {fileURLToPath} from 'node:url';
import {defineConfig} from 'vitest/config';

const stub = name => fileURLToPath(new URL(`src/test/javascript/stubs/${name}.js`, import.meta.url));

// Unit tests of the front-end code. The Cypress suite in tests/ runs against a live Jahia instead.
export default defineConfig({
    resolve: {
        alias: {
            // The host's library, shared and never installed (webpack.config.js)
            '@apollo/client': stub('apollo-client'),
            'react-redux': stub('react-redux'),
            // Reads `document` when it loads: the tests run in Node
            '@jahia/moonstone': stub('moonstone')
        }
    },
    test: {
        include: ['src/test/javascript/**/*.test.js']
    }
});
