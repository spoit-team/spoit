// Run from the repository root: bun src/test/frontend/docs-contracts.test.mjs
import assert from 'node:assert/strict';
import path from 'node:path';

const directory = 'src/main/resources/static/docs';
const html = await Bun.file(path.join(directory, 'index.html')).text();
const ids = new Set();
const anchors = [];
const assets = [];
const sections = [];
const features = [];
const navigation = new Set();
const numbers = new Set();
let codeBlocks = 0;
let tableCount = 0;

await new HTMLRewriter().on('*', {
  element(element) {
    const id = element.getAttribute('id');
    if (id) {
      assert(!ids.has(id), `Duplicate document ID: ${id}`);
      ids.add(id);
    }
    const classes = (element.getAttribute('class') || '').split(/\s+/);
    if (classes.includes('doc-section') || classes.includes('feature-chapter')) {
      assert(id, 'A document section needs an ID');
      const number = element.getAttribute('data-number');
      assert(number && !numbers.has(number), `Missing/duplicate section number: ${id}`);
      assert(element.getAttribute('data-search'), `Missing search keywords: ${id}`);
      numbers.add(number);
      (classes.includes('feature-chapter') ? features : sections).push(id);
    }
    const href = element.getAttribute('href');
    if (href?.startsWith('#')) {
      anchors.push(href.slice(1));
      if (element.hasAttribute('data-section-link')) navigation.add(href.slice(1));
    }
    if (element.tagName === 'pre') codeBlocks++;
    if (element.tagName === 'table') tableCount++;
    for (const attribute of ['href', 'src']) {
      const value = element.getAttribute(attribute);
      if (!value || value.startsWith('#')) continue;
      if (/^https?:\/\//.test(value)) {
        assert(element.tagName === 'a', 'Document assets must work offline');
      } else {
        assert(!value.startsWith('/'), `Use relative assets for file://: ${value}`);
        assets.push(value);
      }
    }
  }
}).transform(new Response(html)).text();

for (const anchor of anchors) assert(ids.has(anchor), `Broken anchor: #${anchor}`);
for (const section of [...sections, ...features]) assert(navigation.has(section), `Section not in navigation: ${section}`);
for (const asset of assets) {
  assert(await Bun.file(path.resolve(directory, asset)).exists(), `Missing local asset: ${asset}`);
}

assert(sections.length >= 11, 'The original integration guide must remain available');
assert.deepEqual(features, [
  'feature-member-auth', 'feature-match', 'feature-clubs', 'feature-facilities',
  'feature-operations', 'feature-system', 'feature-notifications', 'feature-results'
], 'Eight functional chapters must remain separately addressable');
assert(codeBlocks >= 13, 'The original code examples must remain available');
assert(tableCount >= 7, 'The original reference tables must remain available');
for (const required of [
  'app_user', 'club_join_request', 'club_member', 'operating_rule', 'schedule_change',
  'social_match', 'match_application', 'match_participant', 'admin_request',
  'report', 'penalty', 'notification', 'audit_log', 'sport_rating',
  'match_result_submission', 'match_evaluation'
]) {
  assert(html.includes(required), `Missing functional SQL reference: ${required}`);
}

for await (const filename of new Bun.Glob('*.js').scan(directory)) {
  new Bun.Transpiler({ loader: 'js' }).transformSync(await Bun.file(path.join(directory, filename)).text());
}
const cssFiles = [];
for await (const filename of new Bun.Glob('*.css').scan(directory)) cssFiles.push(path.join(directory, filename));
const css = await Bun.build({ entrypoints: cssFiles, write: false });
assert(css.success, css.logs.map(log => log.message).join('\n'));

console.log(`PASS docs: ${sections.length} chapters, ${features.length} features, ${tableCount} tables, ${codeBlocks} code blocks, ${anchors.length} internal links, local assets and JS/CSS syntax.`);
