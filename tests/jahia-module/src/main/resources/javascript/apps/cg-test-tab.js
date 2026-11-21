/*
 * Adds a tab to the drawer of Content Governance, as another module does (docs/drawer-entries.md).
 * Content Governance names this tab nowhere: the Cypress suite checks that it still gets a site
 * switch, a "Fix first" rank and a history. app-shell loads this file when jContent starts, without a
 * build, so it takes React from the share scope of jContent.
 */
(function () {
    const jahia = globalThis.jahia;
    const NS = 'cg-test';
    const FINDINGS = [
        {key: 'cgTestTab.critical', rule: 'cgTestTab.critical', severity: 'critical', title: 'Test tab: a critical finding', detail: 'Fix it first'},
        {key: 'cgTestTab.minor', rule: 'cgTestTab.minor', severity: 'minor', title: 'Test tab: a minor finding'}
    ];

    jahia.i18n.addResourceBundle('en', NS, {
        tab: {label: 'Test tab', help: 'A tab that the test module adds', summary: 'Summary'}
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
    }

    const react = jahia.webpackShareScopes.default.react;
    const version = Object.keys(react).sort().pop();
    react[version].get().then(factory => register(factory()));
}());
