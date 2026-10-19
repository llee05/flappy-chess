import assert from 'node:assert/strict';

/** Actual Chromium touch contacts, including simultaneous fingers and cancellation. */
export function touchControls(page) {
  const contacts = new Map();
  const dispatch = type => page.send('Input.dispatchTouchEvent', { type, touchPoints: [...contacts.values()] });
  const down = async (selector, id = 1) => {
    const point = await page.evaluate(`(() => {
      const button = document.querySelector(${JSON.stringify(selector)});
      if (!button || button.disabled) return null;
      const rect = button.getBoundingClientRect();
      return rect.width && rect.height ? {x: rect.x + rect.width / 2, y: rect.y + rect.height / 2} : null;
    })()`);
    assert(point, `Expected an enabled touch target: ${selector}`);
    contacts.set(id, { ...point, id });
    await dispatch('touchStart');
  };
  const up = async (id = 1) => {
    contacts.delete(id);
    await dispatch('touchEnd');
  };
  return {
    down, up,
    async tap(selector) { await down(selector); await up(); },
    async cancel() { contacts.clear(); await dispatch('touchCancel'); }
  };
}
