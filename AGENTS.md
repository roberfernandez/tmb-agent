# TMBAgent development continuity

Before changing **El meu servei · motor**, read these files in order.

1. `docs/el-meu-servei/CONTINUITY.md`
2. `docs/el-meu-servei/CURRENT_WORK.md`
3. `docs/el-meu-servei/DECISIONS.md`
4. `docs/el-meu-servei/CHANGELOG.md`
5. `docs/el-meu-servei/STATE.json`

The only Android motor source is `miralin-corporatiu-android/`. Its package is
`cat.tmbagent.elmeuservei.motor`. Do not modify or compile `android-overlay/`
or any project named **Miralin Tren Ull** for this product.

GitHub `roberfernandez/tmb-agent` is authoritative. Never continue from an APK,
a temporary Work filesystem, a conversation transcript, or an unpushed branch.
Fetch the repository first and compare the current commit with `STATE.json`.

Every motor change must include automated regression tests and updates to
`CHANGELOG.md`, `CURRENT_WORK.md`, and `STATE.json`. A user-validated release is
recorded separately from a merely built candidate. Never replace the validated
reference until Róber confirms that the candidate works in service.

Signed APKs are built only by `.github/workflows/el-meu-servei-signed-apk.yml`.
From a normal ChatGPT conversation, trigger it by changing only `requestId` in
`.github/el-meu-servei-build.json` after confirming that its package, version and
versionCode match `miralin-corporatiu-android/app/build.gradle`. Never recreate,
replace or export the signing key.

Signing keys, passwords, corporate credentials, and personal service data must
remain outside Git. The public signing-certificate fingerprint may be committed.
