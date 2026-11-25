import React, {useContext, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {HighlightContext} from '../HighlightContext';
import {auditFindingKey, NS_OF, recommendationId, recommendationText} from '../../drawer/findings';
import {FindingMenu} from '../../drawer/FindingActions';
import {DrawerEntryContext} from '../../drawer/DrawerEntryContext';
import {applicableFixers, drawerFixers} from '../../drawer/entries';
import {FixerButtons, FixerPanel, useFixRun} from '../../drawer/Fixers';
import {ResultCard} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import {levelOf} from '../../common/results/levels';
import results from '../../common/results/Results.module.css';
import styles from './Tabs.module.css';

/**
 * "Highlight on the page" for a finding whose check gave a locator, and "Item not found" when the page
 * has no such element any more, such as after an edit or on a page that a script changes.
 */
export function ItemHighlight({locator}) {
    const {t} = useGovernanceTranslation('audit');
    const highlight = useContext(HighlightContext);
    const [missing, setMissing] = useState(false);
    if (!highlight) {
        return null;
    }
    return (
        <span className={styles.itemAction}>
            {missing && <output className={styles.notFound}>{t('recs.notFound')}</output>}
            <Button size="small"
                    variant="outlined"
                    label={t('a11y.highlight')}
                    onClick={() => setMissing(!highlight(locator))}/>
        </span>
    );
}

ItemHighlight.propTypes = {
    locator: PropTypes.string.isRequired
};

/** What a check proposes to write instead of an occurrence, such as the `fix` that a model gives. */
const fixOf = item => (typeof item?.values?.fix === 'string' && item.values.fix !== '' ? item.values.fix : null);

/**
 * The occurrences of a recommendation: each item its check found, with the fix that its check proposes, its
 * Highlight when the check gave a locator, else the action the section gives (`itemAction`), then the fixers that
 * apply (`extra`).
 */
const occurrencesOf = (rec, itemAction, extra) => (rec.items || []).map((item, index) => {
    const own = item.locator ? <ItemHighlight locator={item.locator}/> : (itemAction?.(rec, item) || null);
    const more = extra(index);
    return {
        key: `${index}`,
        content: (
            <>
                <code className={results.code}>{item.text}</code>
                {item.context && <span className={results.muted}> {item.context}</span>}
                {fixOf(item) && <div className={results.muted}>{fixOf(item)}</div>}
                {more.panel}
            </>
        ),
        action: own || more.buttons ? <span className={styles.itemAction}>{own}{more.buttons}</span> : null
    };
});

/**
 * The card of one recommendation, with the fixers of other modules that apply to it (`contentGovernanceFixer`): on
 * the finding, beside its ⋯, and on each occurrence. The proposals of the fixer under way show under the finding
 * or under its occurrence. The card detail ends with the fix that the check proposes for a single occurrence.
 */
function RuleCard({rec, text, id, tab, itemAction}) {
    const {path, language, ruleOf, previewDocument} = useContext(DrawerEntryContext);
    const finding = useMemo(() => ({rule: rec.rule, severity: rec.severity, title: text.title, detail: text.detail, items: rec.items || []}),
        [rec, text.title, text.detail]);
    const fixers = useMemo(() => applicableFixers(drawerFixers(), ruleOf?.(rec.rule) || {id: rec.rule}, finding),
        [ruleOf, rec.rule, finding]);
    const run = useFixRun({finding, path, language, previewDocument});
    const at = run.target ? run.target.index : undefined;
    const buttons = index => (fixers.length > 0 ?
        <span className={styles.itemAction}><FixerButtons fixers={fixers} isDisabled={run.status === 'running'} onRun={fixer => run.start(fixer, index)}/></span> :
        null);
    const single = finding.items.length === 1 ? fixOf(finding.items[0]) : null;
    return (
        <ResultCard level={levelOf(rec.severity)}
                    title={text.title}
                    detail={single ? <>{text.detail}{text.detail && ' '}{single}</> : text.detail}
                    rule={rec.rule}
                    occurrences={occurrencesOf(rec, itemAction, index => ({
                        buttons: buttons(index),
                        panel: at === index ? <FixerPanel run={run}/> : null
                    }))}
                    menu={<>{buttons(null)}<FindingMenu findingKey={auditFindingKey(rec.bucket || tab, id)}/></>}
        >
            {at === null && <FixerPanel run={run}/>}
        </ResultCard>
    );
}

RuleCard.propTypes = {
    rec: PropTypes.object.isRequired,
    text: PropTypes.shape({title: PropTypes.string, detail: PropTypes.string}).isRequired,
    id: PropTypes.string.isRequired,
    tab: PropTypes.string.isRequired,
    itemAction: PropTypes.func
};

/**
 * The recommendations of a section, one result card per rule.
 * Items come from the check of the server or the checks of the browser as {key, rule, severity, params,
 * items?, title, detail}: the texts come from the rule, in the editor's interface language, for a built-in
 * rule and for a rule of another module alike. An audit stored by an older version has no title, and shows
 * its old text. An item with `bucket`, the key of the results that hold it (audit/subdomainResults.js), takes
 * its finding key and the namespace of its old text from that key: the section of its subdomain may be the
 * view of other results.
 */
export function Recommendations({items, ns, tab, title, isQuietWhenEmpty, itemAction}) {
    const {t} = useGovernanceTranslation('audit');

    if (items.length === 0) {
        return isQuietWhenEmpty ? null : <ResultBanner variant="success">{t('recs.none')}</ResultBanner>;
    }
    return (
        <div className={styles.recList}>
            {title && <h4 className={styles.sectionTitle}>{title}</h4>}
            {items.map(rec => {
                const text = recommendationText(rec, rec.bucket ? NS_OF[rec.bucket] : ns, t);
                const id = recommendationId(rec);
                return <RuleCard key={id} rec={rec} text={text} id={id} tab={tab} itemAction={itemAction}/>;
            })}
        </div>
    );
}

Recommendations.propTypes = {
    items: PropTypes.array.isRequired,
    ns: PropTypes.string.isRequired,
    // The tab's key in the results (`ecodesign` for the namespace `eco`)
    tab: PropTypes.string.isRequired,
    // A heading above the cards, when the section shows more than its recommendations
    title: PropTypes.string,
    // Nothing at all when there is no recommendation, for a section that says so elsewhere
    isQuietWhenEmpty: PropTypes.bool,
    // (rec, item) => the action of an item with no locator, such as a Highlight by its text
    itemAction: PropTypes.func
};
