import React, {useCallback, useState} from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {Button} from '@jahia/moonstone';
import {FactBlock} from '../../common/results/FactBlock';
import {PassedFold, ResultCard} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import styles from './Tabs.module.css';

/**
 * GEO-23, for one page: the JSON-LD its content type produces.
 *
 * Shown and copied, never written. Structured data that contradicts the page is
 * worse than none, so a person reads it before it ships - which is also why the
 * gaps are listed beside it rather than quietly filled from somewhere plausible.
 */
export const SchemaTab = ({report}) => {
    const {t} = useGovernanceTranslation('geo');
    const [copied, setCopied] = useState(false);
    const schema = report.schema;

    const snippet = schema && schema.jsonLd ?
        `<script type="application/ld+json">\n${JSON.stringify(schema.jsonLd, null, 2)}\n</script>` :
        '';

    const copy = useCallback(() => {
        // The clipboard API is unavailable over plain http on some hosts, so the
        // snippet below stays selectable as the fallback rather than the only way.
        try {
            navigator.clipboard.writeText(snippet);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch (e) {
            setCopied(false);
        }
    }, [snippet]);

    if (!schema) {
        return <ResultBanner title={t('schema.none')}/>;
    }

    if (!schema.mapped) {
        return (
            <ResultBanner variant="info" title={t('schema.unmappedTitle')}>
                {t('schema.unmapped', {nodeType: schema.nodeType})} {t('schema.unmappedHelp')}
            </ResultBanner>
        );
    }
    const headline = t('schema.headline', {type: schema.schemaType});

    return (
        <div>
            {/*
              * A conflict is the one thing that makes structured data worse than none. Said first,
              * because the snippet below is otherwise perfectly valid and looks fine.
              */}
            {schema.conflict && (
                <ResultCard level="serious"
                            title={t('schema.conflictTitle')}
                            detail={t('schema.conflict', {
                                property: schema.conflict.property,
                                generated: schema.conflict.generated,
                                page: schema.conflict.page
                            })}/>
            )}
            {(schema.missing || []).length > 0 && (
                <ResultCard level="moderate"
                            title={`${headline}: ${t('schema.incomplete', {count: schema.missing.length})}`}
                            detail={t('schema.missing', {properties: schema.missing.join(', ')})}/>
            )}
            {schema.valid && !schema.conflict && <PassedFold items={[{key: 'schema', title: `${headline}: ${t('schema.complete')}`}]}/>}

            <div className={styles.actions}>
                <Button
                    size="big"
                    color="accent"
                    label={copied ? t('schema.copied') : t('schema.copy')}
                    onClick={copy}
                />
            </div>

            <pre className={styles.snippet}>{snippet}</pre>

            <FactBlock title={t('schema.sources')}
                       rows={(schema.sourced || []).map(source => ({key: source.property, label: source.property, value: source.from}))}/>
            <ResultBanner variant="info" title={t('schema.limits')}/>
        </div>
    );
};

SchemaTab.propTypes = {
    report: PropTypes.object.isRequired
};
