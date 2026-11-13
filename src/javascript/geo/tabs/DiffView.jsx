import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {Typography} from '@jahia/moonstone';
import {diffLines, collapse, hasChanges} from '../util/diffLines';
import styles from './Tabs.module.css';
import {withKeys} from '../../common/keys';


const SIGNS = {add: '+', del: '-'};
// What names a row of the diff: its kind and its line, or the number of lines a gap stands for
const rowKey = row => (row.type === 'gap' ? `gap:${row.count}` : `${row.type}:${row.text}`);

/**
 * Shows what an apply would change, before it changes it. Removed lines and
 * added lines are marked with a sign as well as a colour, so the diff still
 * reads for someone who cannot tell the two colours apart.
 */
export const DiffView = ({before, after}) => {
    const {t} = useGovernanceTranslation('geo');
    const rows = collapse(diffLines(before, after));

    if (!hasChanges(rows)) {
        return <Typography variant="caption" className={styles.explain}>{t('diff.identical')}</Typography>;
    }

    const added = rows.filter(r => r.type === 'add').length;
    const removed = rows.filter(r => r.type === 'del').length;

    return (
        <div>
            <Typography variant="caption" className={styles.diffSummary}>{t('diff.summary', {added, removed})}</Typography>
            <pre className={styles.diff}>
                {withKeys(rows, rowKey).map(({item: r, key}) => {
                    if (r.type === 'gap') {
                        return (
                            <span key={key} className={styles.diffGap}>
                                {t('diff.unchanged', {count: r.count})}{'\n'}
                            </span>
                        );
                    }
                    const sign = SIGNS[r.type] || ' ';
                    return (
                        <span key={key} className={styles['diff' + r.type]}>
                            {sign} {r.text}{'\n'}
                        </span>
                    );
                })}
            </pre>
        </div>
    );
};

DiffView.propTypes = {
    before: PropTypes.string,
    after: PropTypes.string
};
