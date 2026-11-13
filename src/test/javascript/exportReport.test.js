import {describe, expect, it} from 'vitest';
import {AI_DIGITAL_SOURCE_TYPE, reportToJson, reportToMarkdown} from '../../javascript/geo/util/exportReport.js';

const t = (key, params) => (params ? `${key} ${JSON.stringify(params)}` : key);

const report = {
    generatedAt: '2026-11-10T14:03:07.512Z',
    provider: 'openai',
    model: 'openrouter/acme:free',
    verdict: 'partial',
    summary: 'The site is mostly reachable.'
};

describe('reportToJson', () => {
    it('states the AI origin of the report, and keeps every field of the report', () => {
        const parsed = JSON.parse(reportToJson(report));
        expect(parsed.digitalSourceType).toBe(AI_DIGITAL_SOURCE_TYPE);
        expect(parsed).toMatchObject(report);
    });
});

describe('reportToMarkdown', () => {
    it('opens with front matter that states the AI origin, the model and the date', () => {
        const lines = reportToMarkdown(report, {site: 'Acme', t}).split('\n');
        expect(lines.slice(0, 6)).toEqual([
            '---',
            `digitalSourceType: "${AI_DIGITAL_SOURCE_TYPE}"`,
            'provider: "openai"',
            'model: "openrouter/acme:free"',
            'generatedAt: "2026-11-10T14:03:07.512Z"',
            '---'
        ]);
    });

    it('leaves out a field the report does not carry, and keeps the AI origin', () => {
        const markdown = reportToMarkdown({summary: 'x'}, {site: 'Acme', t});
        expect(markdown.startsWith(`---\ndigitalSourceType: "${AI_DIGITAL_SOURCE_TYPE}"\n---\n`)).toBe(true);
    });
});
