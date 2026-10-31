import {registry} from '@jahia/ui-extender';

export default function () {
    registry.add('callback', 'content-governance', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            const {default: register} = await import('./init');
            await register();
        }
    });
    console.info('%c Content Governance is activated', 'color: #0086BF');
}
