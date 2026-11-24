import React, {useContext, useState} from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {HighlightContext} from '../HighlightContext';
import {auditFindingKey, NS_OF, recommendationId, recommendationText} from '../../drawer/findings';
import {FindingMenu} from '../../drawer/FindingActions';
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

/**
 * The occurrences of a recommendation: each item its check found, with its Highlight when the check gave a
 * locator, else the action the section gives (`itemAction`).
 */
const occurrencesOf = (rec, itemAction) => (rec.items || []).map((item, index) => ({
    key: `${index}`,
    content: (
        <>
            <code className={results.code}>{item.text}</code>
            {item.context && <span className={results.muted}> {item.context}</span>}
        </>
    ),
    action: item.locator ? <ItemHighlight locator={item.locator}/> : (itemAction?.(rec, item) || null)
}));

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
                return (
                    <ResultCard key={id}
                                level={levelOf(rec.severity)}
                                title={text.title}
                                detail={text.detail}
                                rule={rec.rule}
                                occurrences={occurrencesOf(rec, itemAction)}
                                occurrencesLabel={rec.items?.length > 1 ? t('recs.items', {count: rec.items.length}) : undefined}
                                menu={<FindingMenu findingKey={auditFindingKey(rec.bucket || tab, id)}/>}/>
                );
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
