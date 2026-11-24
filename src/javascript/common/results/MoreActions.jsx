import React, {useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, MoreVert} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../i18n';
import styles from './Results.module.css';

/**
 * The ⋯ button of a result card, and the actions it opens below it. A click outside or Escape closes
 * it, and so does a click on one of its actions.
 */
export const MoreActions = ({children}) => {
    const {t} = useGovernanceTranslation('common');
    const [open, setOpen] = useState(false);
    const root = useRef(null);

    useEffect(() => {
        if (!open) {
            return undefined;
        }
        const onPointer = event => {
            if (root.current && !root.current.contains(event.target)) {
                setOpen(false);
            }
        };
        const onKey = event => {
            if (event.key === 'Escape') {
                setOpen(false);
            }
        };
        document.addEventListener('mousedown', onPointer);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onPointer);
            document.removeEventListener('keydown', onKey);
        };
    }, [open]);

    return (
        <span ref={root} className={styles.more}>
            <Button size="small"
                    variant="ghost"
                    icon={<MoreVert/>}
                    aria-label={t('results.moreActions')}
                    aria-expanded={open}
                    onClick={() => setOpen(value => !value)}/>
            {open && (
                // A click on an action inside closes the list once the action ran
                // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions
                <span className={styles.moreList} onClick={() => setOpen(false)}>
                    {children}
                </span>
            )}
        </span>
    );
};

MoreActions.propTypes = {
    // The actions, as buttons
    children: PropTypes.node.isRequired
};
