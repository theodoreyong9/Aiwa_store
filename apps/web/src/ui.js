export const $ = (id) => document.getElementById(id);
export const short = (s) => (s && s.length > 18 ? `${s.slice(0, 8)}…${s.slice(-6)}` : (s ?? ''));

export function showError(el, message) { el.textContent = message ? String(message) : ''; }

export function flash(button, text) {
  const was = button.textContent;
  button.textContent = text;
  setTimeout(() => { button.textContent = was; }, 1200);
}

/** An id the page shows shortened and copies in full. */
export function setId(id, full) {
  const el = $(id);
  el.textContent = short(full);
  el.dataset.full = full;
  el.title = full;
}

document.addEventListener('click', async (event) => {
  const button = event.target.closest('[data-copy]');
  if (!button) return;
  const el = $(button.dataset.copy);
  try { await navigator.clipboard.writeText(el.dataset.full ?? el.textContent); flash(button, 'Copied'); }
  catch { flash(button, 'Select it'); }
});

export function switchView(name) {
  for (const btn of document.querySelectorAll('#app-nav button')) btn.classList.toggle('active', btn.dataset.view === name);
  for (const view of document.querySelectorAll('.view')) view.hidden = view.dataset.view !== name;
}
