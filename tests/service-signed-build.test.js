import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const workflow = readFileSync('.github/workflows/el-meu-servei-signed-apk.yml', 'utf8');
const request = JSON.parse(readFileSync('.github/el-meu-servei-build.json', 'utf8'));
const gradle = readFileSync('miralin-corporatiu-android/app/build.gradle', 'utf8');
const verifier = readFileSync('scripts/verify-el-meu-servei-motor.sh', 'utf8');

test('the signed build request identifies only the current motor', () => {
  assert.equal(request.product, 'El meu servei · motor');
  assert.equal(request.sourcePath, 'miralin-corporatiu-android');
  assert.equal(request.applicationId, 'cat.tmbagent.elmeuservei.motor');
  assert.equal(request.versionName, gradle.match(/versionName\s+'([^']+)'/)[1]);
  assert.equal(request.versionCode, Number(gradle.match(/versionCode\s+(\d+)/)[1]));
  assert.ok(request.versionCode > request.previousVersionCode);
  assert.equal(request.signingCertificateSha256,
    '8892fa0f4f446685f24eda202b4ee5493792f064291e2bddd7e33060046b7f56');
});

test('GitHub Actions receives every signing value only through repository secrets', () => {
  for (const name of [
    'MOTOR_SIGNING_KEYSTORE_BASE64',
    'MOTOR_SIGNING_STORE_PASSWORD',
    'MOTOR_SIGNING_KEY_ALIAS',
    'MOTOR_SIGNING_KEY_PASSWORD'
  ]) {
    assert.match(workflow, new RegExp(`secrets\\.${name}`));
  }
  assert.doesNotMatch(workflow, /storePassword\s+['"][^$]/);
  assert.doesNotMatch(workflow, /keyPassword\s+['"][^$]/);
  assert.match(workflow, /base64 --decode/);
  assert.match(workflow, /Remove signing store/);
});

test('the workflow verifies identity, version and update compatibility before publishing', () => {
  assert.match(workflow, /cat\.tmbagent\.elmeuservei\.motor/);
  assert.match(workflow, /8892fa0f4f446685f24eda202b4ee5493792f064291e2bddd7e33060046b7f56/);
  assert.match(workflow, /versionCode must be newer/);
  assert.match(workflow, /scripts\/build-el-meu-servei-motor\.sh/);
  assert.match(verifier, /EXPECTED_VERSION_NAME/);
  assert.match(verifier, /EXPECTED_VERSION_CODE/);
  assert.match(verifier, /verify --print-certs \"\$apk\" 2>&1/);
  assert.match(verifier, /certificate SHA-256 digest:/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
  assert.match(workflow, /gh release (create|upload)/);
});

test('a normal ChatGPT conversation can trigger signing with a text-only commit', () => {
  assert.match(workflow, /\.github\/el-meu-servei-build\.json/);
  assert.match(workflow, /workflow_dispatch/);
  assert.equal(typeof request.requestId, 'string');
  assert.ok(request.requestId.length > 0);
});
