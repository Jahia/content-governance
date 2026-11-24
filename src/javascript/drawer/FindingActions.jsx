import React, {useContext} from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {DisplayAction} from '@jahia/ui-extender';
import {useGovernanceTranslation} from '../common/i18n';
import {MoreActions} from '../common/results/MoreActions';
import {DrawerEntryContext} from './DrawerEntryContext';
import {EntryBoundary} from './EntryBoundary';
import {FINDING_ACTIONS_TARGET} from './entries';
import styles from './GovernanceDrawer.module.css';

/**
 * One action of another module, as a small button. Its label is a translation key with its
 * namespace (`my-module:fix`). The click calls the action's `onClick` with every prop, as jContent's
 * buttons do.
 */
const ActionButton = props => {
    const {buttonLabel, buttonLabelParams, buttonIcon, buttonProps, isVisible, enabled, onClick} = props;
    const {t} = useGovernanceTranslation();
    if (isVisible === false) {
        return null;
    }
    return (
        <Button {...buttonProps}
                size="small"
                variant="ghost"
                label={t(buttonLabel, buttonLabelParams)}
                icon={buttonIcon}
                isDisabled={enabled === false}
                onClick={event => onClick?.(props, event)}/>
    );
};

ActionButton.propTypes = {
    buttonLabel: PropTypes.string.isRequired,
    buttonLabelParams: PropTypes.object,
    buttonIcon: PropTypes.node,
    // Attributes for the button, such as data-registry-key
    buttonProps: PropTypes.object,
    isVisible: PropTypes.bool,
    enabled: PropTypes.bool,
    onClick: PropTypes.func
};

/**
 * The actions that other modules add to one finding of the drawer. Each action receives the page,
 * the language, this finding and every finding of the drawer. Nothing when no module adds one. An
 * action that fails leaves the others in place.
 */
export const FindingActions = ({findingKey}) => {
    const {path, language, findings, actions} = useContext(DrawerEntryContext);
    const finding = findings.find(item => item.key === findingKey);
    if (!finding || actions.length === 0) {
        return null;
    }
    return (
        <span className={styles.findingActions}>
            {actions.map(action => (
                <EntryBoundary key={action.key} name={action.key}>
                    <DisplayAction actionKey={action.key}
                                   target={FINDING_ACTIONS_TARGET}
                                   path={path}
                                   language={language}
                                   finding={finding}
                                   findings={findings}
                                   render={ActionButton}/>
                </EntryBoundary>
            ))}
        </span>
    );
};

FindingActions.propTypes = {
    // The key of the finding, as drawer/findings.js builds it
    findingKey: PropTypes.string.isRequired
};

/**
 * The ⋯ of a result card: the actions that other modules add to its finding, such as the
 * assistant's Fix. Nothing when no module adds one or the drawer does not know the finding.
 */
export const FindingMenu = ({findingKey}) => {
    const {findings, actions} = useContext(DrawerEntryContext);
    if (actions.length === 0 || !findings.some(item => item.key === findingKey)) {
        return null;
    }
    return (
        <MoreActions>
            <FindingActions findingKey={findingKey}/>
        </MoreActions>
    );
};

FindingMenu.propTypes = {
    // The key of the finding, as drawer/findings.js builds it
    findingKey: PropTypes.string.isRequired
};
