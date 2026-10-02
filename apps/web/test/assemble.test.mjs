import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assembleHtml } from '../src/assemble.js';

test('scripts and stylesheets of the bundle are put inside index.html, and nothing else is touched', () => {
  const html = assembleHtml({
    'index.html': '<head><link rel="stylesheet" href="css/a.css"><link rel="stylesheet" href="https://cdn.example/x.css"><link rel="icon" href="icon.png"></head>'
      + '<body><script type="module" src="./m.js"></script><script src="https://cdn.example/lib.js"></script><script src="missing.js"></script><script>inline()</script></body>',
    'css/a.css': 'a{}',
    'm.js': 'export const x = 1;',
  });
  assert.equal(html,
    '<head><style>a{}</style><link rel="stylesheet" href="https://cdn.example/x.css"><link rel="icon" href="icon.png"></head>'
    + '<body><script type="module">export const x = 1;</script><script src="https://cdn.example/lib.js"></script><script src="missing.js"></script><script>inline()</script></body>');
});

test('a reference that leaves the bundle is never resolved: addresses, absolute paths, parent folders', () => {
  const files = { 'index.html': '<script src="/etc/a.js"></script><script src="../b.js"></script><script src="//x/c.js"></script><script src="data:text/javascript,1"></script>', 'etc/a.js': 'A', 'b.js': 'B' };
  assert.equal(assembleHtml(files), files['index.html']);
});

test('a closing tag inside a file cannot end its element early', () => {
  const html = assembleHtml({ 'index.html': '<link rel=stylesheet href=s.css><script src="a.js"></script>', 's.css': 'x{}</style><b>', 'a.js': 'x="</SCRIPT>"' });
  assert.ok(!/<\/style><b>/.test(html));
  assert.ok(!/"<\/SCRIPT>"/i.test(html));
});

test('an app without index.html is not an app', () => {
  assert.throws(() => assembleHtml({ 'a.js': '1' }), /no index\.html/);
});
