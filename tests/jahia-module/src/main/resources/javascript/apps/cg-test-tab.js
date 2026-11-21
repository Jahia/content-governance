/*
 * Adds a tab, a panel and an action on a finding to the drawer of Content Governance, as another
 * module does (docs/drawer-entries.md). Content Governance names them nowhere: the Cypress suite
 * checks that the tab still gets a site switch, a "Fix first" rank and a history, and that the panel
 * gets a button, a site switch, and the context of the finding that the action opens it with, as the
 * chat of AI Assistant does. app-shell loads this file when jContent starts, without a build, so it
 * takes React from the share scope of jContent.
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
        panel: {label: 'Test panel', button: 'Test panel', help: 'A panel that the test module adds', open: 'Open in the test panel'}
    }, true, true);

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
