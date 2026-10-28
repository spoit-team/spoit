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

// 관리자 화면 전환 시 admin-shell.js가 .admin-view로 초점을 옮긴다.
// 뷰 전체를 감싸는 기본 초점 테두리는 끄고, 키보드일 때 제목에 표시를 남긴다.
const adminCss = await readFile(path.join(resources, 'static/css/admin.css'), 'utf8');
check(/\.admin-view:focus,\s*\.admin-view:focus-visible\s*\{[^}]*outline:\s*none/.test(adminCss),
  'admin.css: .admin-view must not draw the full-viewport focus ring');
check(/\.admin-view:focus-visible\s+\.page-heading-copy\s*\{[^}]*outline:\s*(?!none\b|0)\S/.test(adminCss),
  'admin.css: keyboard focus on .admin-view must mark the page title instead');

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

// 관리자 진입 버튼은 역할별로만 노출하고, 관리자 화면끼리는 서로 이동 링크를 두지 않는다.
function fragmentSlice(source, openMarker, closeMarker) {
  const start = source.indexOf(openMarker);
  if (start < 0) return null;
  const end = source.indexOf(closeMarker, start);
  return end < 0 ? null : source.slice(start, end);
}

function anchorsOf(source) {
  return Array.from(source.matchAll(/<a\b[^>]*>/g), match => match[0]);
}

const layoutSource = await readFile(path.join(resources, 'templates/fragments/layout.html'), 'utf8');
const adminSource = await readFile(path.join(resources, 'templates/fragments/admin.html'), 'utf8');

const siteHeader = fragmentSlice(layoutSource, '<header th:fragment="siteHeader(active)"', '</header>');
check(Boolean(siteHeader), 'layout.html: siteHeader fragment missing');
const headerAdminLinks = anchorsOf(siteHeader ?? '').filter(tag => tag.includes('/admin/'));
check(headerAdminLinks.length === 2, `layout.html: expected 2 admin entry links, found ${headerAdminLinks.length}`);
const facilityEntry = headerAdminLinks.find(tag => tag.includes('@{/admin/facility}'));
const systemEntry = headerAdminLinks.find(tag => tag.includes('@{/admin/system}'));
check(Boolean(facilityEntry), 'layout.html: missing facility admin entry link');
check(Boolean(systemEntry), 'layout.html: missing system admin entry link');
check(/th:if="\$\{[^"]*currentMember != null[^"]*roleLabel == '시설 관리자'[^"]*\}"/.test(facilityEntry ?? ''),
  'layout.html: facility admin entry must be gated to 시설 관리자');
check(/th:if="\$\{[^"]*currentMember != null[^"]*roleLabel == '시스템 관리자'[^"]*\}"/.test(systemEntry ?? ''),
  'layout.html: system admin entry must be gated to 시스템 관리자');
check(headerAdminLinks.every(tag => tag.includes('th:if=')),
  'layout.html: admin entry links must not render for general members');

// 템플릿의 역할 라벨은 MemberRole이 만드는 라벨과 같아야 한다(라벨이 바뀌면 버튼이 사라진다).
const memberRoleSource = await readFile(
  path.resolve('src/main/java/co/kr/spoit/member/domain/MemberRole.java'), 'utf8');
const currentMemberSource = await readFile(
  path.resolve('src/main/java/co/kr/spoit/member/dto/CurrentMemberDto.java'), 'utf8');
check(currentMemberSource.includes('private final String roleLabel;'),
  'CurrentMemberDto: roleLabel property used by the header gating must exist');
for (const [role, expected] of [
  ['FACILITY_MANAGER', '시설 관리자'],
  ['ADMIN', '시스템 관리자']
]) {
  check(memberRoleSource.includes(`${role}("ROLE_${role}", "${expected}")`),
    `MemberRole.${role} label must stay "${expected}"`);
  check(layoutSource.includes(`roleLabel == '${expected}'`),
    `layout.html: admin entry gating must use MemberRole label "${expected}"`);
}

const facilitySidebar = fragmentSlice(adminSource, '<aside th:fragment="facilitySidebar(active)"', '</aside>');
const systemSidebar = fragmentSlice(adminSource, '<aside th:fragment="systemSidebar(active)"', '</aside>');
check(Boolean(facilitySidebar), 'admin.html: facilitySidebar fragment missing');
check(Boolean(systemSidebar), 'admin.html: systemSidebar fragment missing');
check(!/th:href="@\{\/admin\/system/.test(facilitySidebar ?? ''),
  'admin.html: facility sidebar must not link to the system admin page');
check(!/th:href="@\{\/admin\/facility/.test(systemSidebar ?? ''),
  'admin.html: system sidebar must not link to the facility admin page');
check(!(facilitySidebar ?? '').includes('role-switch') && !(systemSidebar ?? '').includes('role-switch'),
  'admin.html: cross-admin role switch must stay removed');
check(/th:href="@\{\/admin\/facility/.test(facilitySidebar ?? ''),
  'admin.html: facility sidebar must keep its own menu links');
check(/th:href="@\{\/admin\/system/.test(systemSidebar ?? ''),
  'admin.html: system sidebar must keep its own menu links');

// 실제 시설·시스템 템플릿의 모든 .admin-view가 대체 포커스 표시 대상(제목)을 가져야 한다.
for (const file of ['templates/admin/facility.html', 'templates/admin/system.html']) {
  const source = await readFile(path.join(resources, file), 'utf8');
  const views = source.split(/<section class="admin-view"/).slice(1);
  const missing = views.filter(view => !/class="page-heading-copy"[\s\S]*<h1\b/.test(view));
  check(views.length > 0 && missing.length === 0,
    `${file}: every .admin-view needs .page-heading-copy with an h1 for the keyboard focus indicator`);
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`PASS: ${scripts.length} JS, ${styles.length} CSS, ${templates.length} templates; ${assertions} contract checks.`);
}
