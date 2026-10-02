// An app of kind aiwa is several files; it runs from ONE document in its sandbox, which has no address to fetch the
// others from. So the scripts and stylesheets of the bundle that index.html names are put inside it. Text files only;
// an image or a font goes in as a data: address. A reference to anything outside the bundle is left alone.

const localPath = (ref) => {
  if (/^([a-z][a-z0-9+.-]*:|\/\/|\/|#)/i.test(ref)) return null;
  const clean = ref.split(/[?#]/)[0].replace(/^\.\//, '');
  return clean && !clean.includes('..') ? clean : null;
};

const attr = (tag, name) => new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`, 'i').exec(tag)?.slice(1).find((v) => v !== undefined) ?? null;

// A closing tag inside inlined text would end its element early.
const protect = (text, tag) => text.replace(new RegExp(`</(${tag})`, 'gi'), '<\\/$1');

/**
 * @param {Record<string, string>} files path -> text, with an index.html
 * @returns {string} the document
 */
export function assembleHtml(files) {
  const html = files['index.html'];
  if (typeof html !== 'string') throw new Error('The app has no index.html');
  const withScripts = html.replace(/<script\b([^>]*)>\s*<\/script>/gi, (whole, attrs) => {
    const src = attr(attrs, 'src');
    const path = src === null ? null : localPath(src);
    if (path === null || typeof files[path] !== 'string') return whole;
    const rest = attrs.replace(/\bsrc\s*=\s*(?:"[^"]*"|'[^']*')/i, '').trim();
    return `<script${rest ? ` ${rest}` : ''}>${protect(files[path], 'script')}</script>`;
  });
  return withScripts.replace(/<link\b[^>]*>/gi, (tag) => {
    if (!/\brel\s*=\s*["']?stylesheet/i.test(tag)) return tag;
    const href = attr(tag, 'href');
    const path = href === null ? null : localPath(href);
    if (path === null || typeof files[path] !== 'string') return tag;
    return `<style>${protect(files[path], 'style')}</style>`;
  });
}
