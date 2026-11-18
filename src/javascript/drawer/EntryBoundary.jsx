import React from 'react';
import PropTypes from 'prop-types';

/**
 * Keeps the drawer open when an entry of another module fails: the entry gives way to its
 * fallback, and the error goes to the console.
 */
export class EntryBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {failed: false};
    }

    static getDerivedStateFromError() {
        return {failed: true};
    }

    componentDidCatch(error) {
        console.error(`[content-governance] the drawer entry "${this.props.name}" failed`, error);
    }

    render() {
        return this.state.failed ? (this.props.fallback ?? null) : this.props.children;
    }
}

EntryBoundary.propTypes = {
    // The registry key or target of the entry, for the console
    name: PropTypes.string.isRequired,
    fallback: PropTypes.node,
    children: PropTypes.node.isRequired
};
