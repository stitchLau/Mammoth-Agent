/**
 * Mammoth M0 spike — Client half. Registers a purely decorative badge in the composer's
 * allocated dock slot so the spike proves a UI slot can be contributed by an out-of-tree
 * bundle. No text, no host state, no side effects; removing the bundle removes the badge.
 */
window.__ModuleLoader__.load({
  id: '@local/mammoth-spike',
  factory(require) {
    const React = require('react')
    const h = React.createElement

    function MammothSpikeBadge() {
      return h(
        'svg',
        {
          viewBox: '0 0 24 24',
          width: 18,
          height: 18,
          role: 'img',
          'aria-label': 'Mammoth spike bundle',
          style: { display: 'block', pointerEvents: 'none', opacity: 0.85 },
        },
        h('path', { d: 'M3 17c4 0 7-2 9-6 2 4 5 6 9 6', fill: 'none', stroke: '#247bbf', strokeWidth: 2.4, strokeLinecap: 'round' }),
        h('circle', { cx: 12, cy: 8, r: 2.6, fill: '#247bbf' }),
      )
    }

    return {
      inject: ['slots'],
      apply(ctx) {
        ctx.slots.inject('conversation.composer.dock', () => ctx.slots.register({
          name: 'conversation.composer.dock',
          id: 'mammoth-spike',
          order: 50,
        }, MammothSpikeBadge))
      },
    }
  },
})
