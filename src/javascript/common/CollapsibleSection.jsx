import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, ChevronDown, ChevronUp, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from './i18n';
import {CountBadge} from './CountBadge';
import {HelpTab} from './HelpTab';
import styles from './CollapsibleSection.module.css';

/**
 * A subdomain of a drawer domain, such as Accessibility in Page quality. Its header opens and closes
 * it, and shows its count of defects, its title in capitals and its help as a tooltip. The content
 * mounts the first time the section opens and stays mounted, so a section closed and opened again
 * keeps what the editor expanded inside it.
 *
 * `count` is null when the section has no count to give: its check did not run or did not finish.
 */
export const CollapsibleSection = ({id, label, help, note, status, count, isSerious, isOpen, onToggle, children}) => {
    const [mounted, setMounted] = useState(isOpen);
    useEffect(() => {
        if (isOpen) {
            setMounted(true);
        }
    }, [isOpen]);
    const panelId = `${id}-panel`;
    const Chevron = isOpen ? ChevronUp : ChevronDown;

    return (
        <div className={styles.section}>
            <HelpTab help={help} note={note} isSelected={isOpen} className={styles.anchor}>
                <button type="button"
                        id={id}
                        className={styles.header}
                        aria-expanded={isOpen}
                        aria-controls={panelId}
                        onClick={onToggle}
                >
                    <span className={styles.count}>
                        {typeof count === 'number' ? <CountBadge count={count} isSerious={isSerious}/> : <span aria-hidden="true">–</span>}
                    </span>
                    <Typography component="span" weight="bold" className={styles.title}>{label}</Typography>
                    {status && <Typography component="span" variant="caption" className={styles.status}>{status}</Typography>}
                    <Chevron className={styles.chevron} aria-hidden="true"/>
                </button>
            </HelpTab>
            <section id={panelId} aria-labelledby={id} className={styles.body} hidden={!isOpen}>
                {mounted && children}
            </section>
        </div>
    );
};

CollapsibleSection.propTypes = {
    // The id of the header; the content takes `<id>-panel`
    id: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    // What the section checks, as a tooltip on its header
    help: PropTypes.string.isRequired,
    // Added to the help, such as how many issues the count holds
    note: PropTypes.string,
    // A short state beside the title, such as "Not configured"
    status: PropTypes.string,
    // Its defects, or null when it has no count
    count: PropTypes.number,
    // One defect at least is critical or serious: the count is red
    isSerious: PropTypes.bool,
    isOpen: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired,
    children: PropTypes.node
};

/**
 * "Expand all" while a section is closed, else "Collapse all".
 *
 * @param {{ids: Array<string>, isOpen: Function, setOpen: Function}} props the sections and their state (`useOpenSections`)
 */
export const ExpandAll = ({ids, isOpen, setOpen}) => {
    const {t} = useGovernanceTranslation('drawer');
    if (ids.length < 2) {
        return null;
    }
    const anyClosed = ids.some(id => !isOpen(id));
    return (
        <Button variant="ghost"
                label={t(anyClosed ? 'expandAll' : 'collapseAll')}
                onClick={() => setOpen(ids, anyClosed)}/>
    );
};

ExpandAll.propTypes = {
    ids: PropTypes.arrayOf(PropTypes.string).isRequired,
    isOpen: PropTypes.func.isRequired,
    setOpen: PropTypes.func.isRequired
};
