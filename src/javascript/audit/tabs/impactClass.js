import styles from './Tabs.module.css';

/** The classes of a severity chip: the chip, and the colour of its severity. */
export const impactClass = severity => `${styles.impact} ${styles['impact_' + severity]}`;
