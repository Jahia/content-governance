/*
 * Adds a tab, a panel and an action on a finding to the drawer of Content Governance, as another
 * module does (docs/drawer-entries.md). Content Governance names them nowhere: the Cypress suite
 * checks that the tab still gets a site switch, a "Fix first" rank and a history, and that the panel
 * gets a button, a site switch, and the context of the finding that the action opens it with, as the
 * chat of AI Assistant does. It also adds two fixers of the rule links.mixedContent, which the
 * Cypress suite drives through `window.cgTestFixers`. app-shell loads this file when jContent starts,
 * without a build, so it takes React from the share scope of jContent.
 */
(function () {
    const jahia = globalThis.jahia;
    const NS = 'cg-test';
    const FINDINGS = [
        {key: 'cgTestTab.critical', rule: 'cgTestTab.critical', severity: 'critical', title: 'Test tab: a critical finding', detail: 'Fix it first'},
        {key: 'cgTestTab.minor', rule: 'cgTestTab.minor', severity: 'minor', title: 'Test tab: a minor finding'}
    ];

    jahia.i18n.addResourceBundle('en', NS, {
        tab: {label: 'Test tab', help: 'A tab that the test module adds', summary: 'Summary'},
        panel: {label: 'Test panel', button: 'Test panel', help: 'A panel that the test module adds', open: 'Open in the test panel'},
        fixer: {label: 'Test fix', other: 'Test fix: other'}
    }, true, true);

    /**
     * What the Cypress suite reads and sets on the fixers. `hold()` keeps every run in the running state
     * until `release()`. `fail` makes a run reject with that message, and `empty` makes it answer no
     * proposal. `runs` and `applied` record what each run and each apply received.
     */
    const control = {
        held: false,
        waiters: [],
        fail: null,
        empty: false,
        runs: [],
        applied: [],
        hold() {
            control.held = true;
        },
        release() {
            control.held = false;
            control.waiters.splice(0).forEach(resolve => resolve());
        }
    };
    globalThis.cgTestFixers = control;

    /** Resolves after the hold, or at once with no hold; rejects when the drawer cancels the run. */
    const wait = signal => new Promise((resolve, reject) => {
        signal.addEventListener('abort', () => reject(new Error('cancelled')));
        if (control.held) {
            control.waiters.push(resolve);
        } else {
            setTimeout(resolve, 50);
        }
    });

    const isMixedContent = ({rule}) => rule.id === 'links.mixedContent';

    // Two proposals and a note for each insecure address, and an apply that records the text it gets
    jahia.uiExtender.registry.add('contentGovernanceFixer', 'cgTestFix', {
        label: `${NS}:fixer.label`,
        appliesTo: isMixedContent,
        run: ({occurrence, path, language, signal}) => {
            const address = occurrence ? occurrence.text : '';
            control.runs.push({fixer: 'cgTestFix', occurrence: address, path, language});
            return wait(signal).then(() => {
                if (control.fail) {
                    throw new Error(control.fail);
                }
                if (control.empty) {
                    return {proposals: []};
                }
                return {
                    proposals: [
                        {text: address.replace('http://', 'https://'), original: address, reason: 'The same address over HTTPS'},
                        {text: address.replace('http://', 'https://www.'), original: address}
                    ],
                    note: 'Test note: both addresses answer over HTTPS.'
                };
            });
        },
        apply: ({proposal, occurrence}) => {
            control.applied.push({text: proposal.text, occurrence: occurrence ? occurrence.text : null});
            return Promise.resolve();
        }
    });

    // A second fixer of the same rule: the drawer puts it in the ⋮ of the card
    jahia.uiExtender.registry.add('contentGovernanceFixer', 'cgTestFixOther', {
        label: `${NS}:fixer.other`,
        appliesTo: isMixedContent,
        run: ({occurrence, signal}) => {
            const address = occurrence ? occurrence.text : '';
            control.runs.push({fixer: 'cgTestFixOther', occurrence: address});
            return wait(signal).then(() => ({proposals: [{text: `${address}#other`}]}));
        }
    });

    function register(React) {
        const h = React.createElement;

        function TestTab({path, isOn, onCount, onFindings, saveFindings, fetchHistory}) {
            const [stored, setStored] = React.useState(null);
            const [runs, setRuns] = React.useState(null);

            React.useEffect(() => {
                onCount(FINDINGS.length);
                onFindings(FINDINGS);
            }, [onCount, onFindings]);

            const store = () => saveFindings(FINDINGS)
                .then(run => {
                    setStored(run);
                    return fetchHistory();
                })
                .then(setRuns);

            return h('div', {'data-cg-test-tab': path},
                isOn('summary') && h('p', {'data-cg-test-summary': ''}, 'The summary of the test tab'),
                h('button', {type: 'button', onClick: store}, 'Store the findings'),
                stored && h('p', {'data-cg-test-stored': stored.summary.total}, `Stored ${stored.summary.total} findings`),
                runs && h('p', {'data-cg-test-runs': runs.length}, `${runs.length} stored runs`));
        }

        jahia.uiExtender.registry.add('contentGovernanceDrawerTab', 'cgTestTab', {
            targets: ['contentGovernanceDrawer:50'],
            label: `${NS}:tab.label`,
            help: `${NS}:tab.help`,
            tabs: [{id: 'summary', label: `${NS}:tab.summary`}],
            component: TestTab
        });

        function TestPanel({path, language, context}) {
            const finding = context && context.finding;
            return h('div', {'data-cg-test-panel': path},
                h('p', null, 'The panel of the test module'),
                finding && h('p', {'data-cg-test-context': finding.rule, 'data-cg-test-language': language},
                    `Opened on ${finding.title}`));
        }

        jahia.uiExtender.registry.add('contentGovernancePanel', 'cgTestPanel', {
            label: `${NS}:panel.label`,
            buttonLabel: `${NS}:panel.button`,
            order: 50,
            settingsHelp: `${NS}:panel.help`,
            component: TestPanel
        });

        // Opens the test panel with the finding, as "Fix with the assistant" opens the chat
        jahia.uiExtender.registry.add('action', 'cgTestOpenPanel', {
            targets: ['contentGovernanceFindingActions:50'],
            buttonLabel: `${NS}:panel.open`,
            onClick: ({path, language, finding}) => jahia.uiExtender.registry.get('contentGovernanceApi', 'drawer')
                .openPanel('cgTestPanel', {finding, path, language})
        });
    }

    const react = jahia.webpackShareScopes.default.react;
    const version = Object.keys(react).sort().pop();
    react[version].get().then(factory => register(factory()));
}());
