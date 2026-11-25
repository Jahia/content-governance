import React from 'react';

// @jahia/moonstone reads `document` when it loads, and unit tests run in Node with no DOM. The
// components the drawer tests render are plain elements here; any other is undefined.
const h = React.createElement;

export const Button = ({label, icon, isDisabled, onClick, size, variant, ...rest}) => h('button', {type: 'button', disabled: isDisabled, 'data-size': size, 'data-variant': variant, ...rest}, icon, label);
export const Typography = ({children, id, title, className}) => h('span', {id, title, className}, children);
export const Tab = ({children, ...rest}) => h('div', rest, children);
export const TabItem = ({label, icon, isSelected, isDisabled, size, onClick, ...rest}) => h('button', {type: 'button', disabled: isDisabled, ...rest}, icon, label);
export const Tooltip = ({children}) => children;
export const Loader = () => h('span', null, 'loading');
export const Close = () => null;
export const Chip = ({label}) => h('span', null, label);
// Moonstone draws the title of a banner in bold, above its text
export const Banner = ({title, children, className}) => h('div', {role: 'status', className}, title && h('strong', null, title), children);
// Icons draw an empty svg here, with the props they get, such as an aria-label
const icon = ({size, color, ...rest}) => h('svg', rest);
export const Reload = icon;
export const Download = icon;
export const Visibility = icon;
export const ChevronDown = icon;
export const ChevronUp = icon;
export const Check = icon;
export const Edit = icon;
export const Information = icon;
export const Warning = icon;
export const Report = icon;
export const HelpOutline = icon;
