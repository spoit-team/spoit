// Run from the repository root: bun src/test/frontend/check-contracts.mjs
// Optional: --baseline=HEAD also checks that markup and literal routes stay intact.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

const resources = path.resolve('src/main/resources');
const baseline = process.argv.find(argument => argument.startsWith('--baseline='))?.split('=')[1];
const failures = [];
const formNames = new Set();
const namedForms = new Map();
let assertions = 0;

function check(condition, message) {
  assertions++;
  if (!condition) failures.push(message);
}

async function files(pattern) {
  return Array.fromAsync(new Bun.Glob(pattern).scan({ cwd: resources }));
}

async function inspectHtml(html) {
  const elements = [];
  const forms = [];
  let currentForm = null;
  const response = new HTMLRewriter()
    .on('*', {
      element(element) {
        const attributes = Object.fromEntries(element.attributes);
        elements.push({ tag: element.tagName, attributes });
        if (element.tagName === 'form') {
          currentForm = { attributes, inputs: [] };
          forms.push(currentForm);
          element.onEndTag(() => { currentForm = null; });
        }
        if (currentForm && ['input', 'select', 'textarea'].includes(element.tagName)) {
          currentForm.inputs.push(attributes);
        }
      }
    })
    .transform(new Response(html));
  await response.text();
  return { elements, forms };
}

const invariantAttributes = [
  'id', 'class', 'type', 'method', 'role', 'hidden', 'required', 'disabled',
  'min', 'max', 'minlength', 'maxlength', 'pattern', 'multiple', 'autocomplete'
];

function markupSignature(elements) {
  return elements.map(({ tag, attributes }) => [
    tag,
    invariantAttributes.map(name => attributes[name] ?? null),
    Object.keys(attributes).filter(name => name.startsWith('data-')).sort()
  ]);
}

function literalRoutes(elements) {
  return elements.flatMap(({ attributes }) => Object.entries(attributes)
    .filter(([name]) => ['th:action', 'th:href', 'th:src'].includes(name))
    .flatMap(([, value]) => {
      const route = value.match(/@\{(\/[^\s(]*)/);
      if (!route) return [];
      return [route[1].replace(/\{[A-Za-z]\w*\}/g, '{}').replace(/\}$/, '')];
    }));
}

const scripts = await files('static/js/*.js');
for (const file of scripts) {
  const source = await readFile(path.join(resources, file), 'utf8');
  try {
    new Bun.Transpiler({ loader: 'js' }).transformSync(source);
    check(true, file);
  } catch (error) {
    check(false, `${file}: ${error.message}`);
  }
}

const styles = await files('static/css/*.css');
const css = await Bun.build({
  entrypoints: styles.map(file => path.join(resources, file)),
  target: 'browser',
  write: false
});
check(css.success, `CSS syntax: ${css.logs.join('\n')}`);

const templates = await files('templates/**/*.html');
for (const file of templates) {
  const source = await readFile(path.join(resources, file), 'utf8');
  const { elements, forms } = await inspectHtml(source);
  for (const { tag, attributes } of elements) {
    if (['input', 'select', 'textarea'].includes(tag) && attributes.name) formNames.add(attributes.name);
  }
  for (const form of forms) {
    if (form.attributes.id) namedForms.set(form.attributes.id, form);
  }
  const ids = new Set();
  for (const { attributes } of elements) {
    if (attributes.id) {
      // Fragment variants can deliberately reuse IDs in mutually exclusive branches.
      if (!file.replaceAll('\\', '/').startsWith('templates/fragments/')) {
        check(!ids.has(attributes.id), `${file}: duplicate static ID ${attributes.id}`);
      }
      ids.add(attributes.id);
    }
    for (const name of ['th:href', 'th:src']) {
      const reference = attributes[name]?.match(/^@\{\/(css|js|images)\/([^{}]+)\}$/);
      if (reference) {
        const asset = path.join(resources, 'static', reference[1], reference[2]);
        check(await Bun.file(asset).exists(), `${file}: missing asset ${asset}`);
      }
    }
  }
  for (const form of forms) {
    check(Boolean(form.attributes['th:action']), `${file}: form missing th:action`);
    if (form.attributes.method?.toLowerCase() !== 'post') continue;
    check(form.inputs.some(input => input['th:name'] === '${_csrf.parameterName}'
      && input['th:value'] === '${_csrf.token}'), `${file}: POST form missing CSRF binding`);
  }
  if (baseline) {
    const result = Bun.spawnSync(['git', 'show', `${baseline}:src/main/resources/${file.replaceAll('\\', '/')}`]);
    check(result.exitCode === 0, `${file}: baseline unavailable`);
    if (result.exitCode !== 0) continue;
    const previous = await inspectHtml(result.stdout.toString());
    try {
      assert.deepEqual(markupSignature(elements), markupSignature(previous.elements));
      assert.deepEqual(literalRoutes(elements), literalRoutes(previous.elements));
      check(true, file);
    } catch {
      check(false, `${file}: structural markup, validation attributes, DOM hooks or literal routes changed`);
    }
  }
}

const requiredBindings = {
  'selection-form': ['applicationType', 'clubId', 'clubMemberIds'],
  'team-create-form': ['clubName', 'sportId', 'regionId'],
  'team-edit-form': ['clubName', 'sportId', 'regionId'],
  'team-transfer-form': ['clubMemberId'],
  'team-remove-form': ['clubMemberId'],
  'team-disband-form': ['clubNameConfirmation'],
  'facility-form': ['concurrentMatchLimit'],
  'room-editor-form': ['facilityId', 'sportId', 'teamACapacity', 'teamBCapacity', 'feePerPerson'],
  'profile-form': ['nickname', 'regionId', 'representativeSportId'],
  'signup-form': ['nickname', 'regionId']
};
for (const [id, names] of Object.entries(requiredBindings)) {
  const form = namedForms.get(id);
  check(Boolean(form), `Missing form ${id}`);
  for (const name of names) {
    check(Boolean(form?.inputs.some(input => input.name === name)), `${id}: missing binding ${name}`);
  }
}
for (const file of scripts) {
  const source = await readFile(path.join(resources, file), 'utf8');
  for (const selector of source.matchAll(/(?:input|select|textarea)\[name=["']([A-Za-z]\w*)["']\]/g)) {
    check(formNames.has(selector[1]), `${file}: selector references missing form field ${selector[1]}`);
  }
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`PASS: ${scripts.length} JS, ${styles.length} CSS, ${templates.length} templates; ${assertions} contract checks.`);
}
