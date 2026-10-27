const {test} = require('node:test');
const assert = require('node:assert/strict');
const {versionCode, assertCurrent, assertIncreasing, listAllReleases} = require('./firebase_policy.cjs');

test('retrying an old run allocates from current time, not the old run number', () => {
  assert.equal(versionCode(1793145600), 215308800);
  assert.ok(versionCode(1793145660) > versionCode(1793145600));
  assert.throws(() => versionCode(0));
});
test('superseded commits cannot publish', () => {
  assert.doesNotThrow(() => assertCurrent('a'.repeat(40), 'a'.repeat(40)));
  assert.throws(() => assertCurrent('a'.repeat(40), 'b'.repeat(40)));
});
test('older or equal version cannot replace a newer distributed build', () => {
  assert.throws(() => assertIncreasing(2502, [{buildVersion:'2601'}]));
  assert.throws(() => assertIncreasing(2601, [{buildVersion:'2601'}]));
  assert.doesNotThrow(() => assertIncreasing(2602, [{buildVersion:'2601'}]));
  assert.doesNotThrow(() => assertIncreasing(2602, []));
});
test('checks every release page, including an older high-version release', async () => {
  const pages = [];
  const client = {request: async ({params}) => {
    pages.push(params.pageToken);
    return {data: params.pageToken ? {releases:[{buildVersion:'9000'}]} : {releases:[{buildVersion:'2601'}], nextPageToken:'second'}};
  }};
  const releases = await listAllReleases(client, '1:123:android:abc');
  assert.deepEqual(pages, [undefined, 'second']);
  assert.throws(() => assertIncreasing(2700, releases));
});
