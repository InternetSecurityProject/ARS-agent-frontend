// Firebase publication guards; no secrets are printed, including on HTTP/auth failures.
const EPOCH = 1577836800; // 2020-01-01, stays inside Android's versionCode range until 2086.
function versionCode(seconds = Math.floor(Date.now() / 1000)) {
  const value = seconds - EPOCH;
  if (!Number.isSafeInteger(value) || value < 1 || value > 2100000000) throw Error('Invalid version clock');
  return value;
}
function assertCurrent(expected, current) {
  if (!/^[a-f0-9]{40}$/.test(expected || '') || expected !== current) throw Error('Superseded feature commit');
}
function assertIncreasing(value, releases) {
  if (!Number.isSafeInteger(value) || value < 1 || releases.some(r => {
    const existing = Number(r.buildVersion);
    return !Number.isSafeInteger(existing) || existing >= value;
  })) throw Error('APK version is not newer than all existing Firebase releases');
}
async function listAllReleases(client, appId) {
  const project = /^1:([0-9]+):android:[a-f0-9]+$/.exec(appId || '')?.[1];
  if (!project) throw Error('Invalid Firebase Android app');
  const url = `https://firebaseappdistribution.googleapis.com/v1/projects/${project}/apps/${encodeURIComponent(appId)}/releases`;
  const releases = [];
  let pageToken;
  do {
    const {data} = await client.request({url, params:{pageSize:1000, pageToken}, timeout:30000});
    if (data.releases && !Array.isArray(data.releases)) throw Error('Malformed release list');
    releases.push(...(data.releases || []));
    pageToken = data.nextPageToken;
  } while (pageToken);
  return releases;
}
module.exports = {versionCode, assertCurrent, assertIncreasing, listAllReleases};
if (require.main === module) {
  (async () => {
    if (process.argv[2] === 'version') { console.log(versionCode()); return; }
    if (process.argv[2] === 'current') {
      assertCurrent(process.env.GITHUB_SHA, process.env.CURRENT_FEATURE_SHA); return;
    }
    if (process.argv[2] !== 'preflight') throw Error('Unknown publication guard');
    const {GoogleAuth} = require('google-auth-library'); // Provided by pinned firebase-tools in CI.
    const client = await new GoogleAuth({scopes:['https://www.googleapis.com/auth/cloud-platform']}).getClient();
    assertIncreasing(Number(process.env.APP_VERSION_CODE), await listAllReleases(client, process.env.FIREBASE_APP_ID));
    console.log('Firebase version publication guard passed.');
  })().catch(() => {
    console.error('Firebase publication guard failed. Check current commit, version and App Distribution read permission. Credentials are not logged.');
    process.exitCode = 1;
  });
}
