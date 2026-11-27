#!/usr/bin/env bash
set -euo pipefail

apk=${1:?"Uso: verify-el-meu-servei-motor.sh APK [APKSIGNER] [AAPT]"}
apksigner=${2:-${APKSIGNER:-}}
aapt=${3:-${AAPT:-}}
expected_version_name=${EXPECTED_VERSION_NAME:-}
expected_version_code=${EXPECTED_VERSION_CODE:-}

if [[ -z "$apksigner" || -z "$aapt" ]]; then
  build_tools=${ANDROID_HOME:-}/build-tools
  [[ -d "$build_tools" ]] || { echo "Falta ANDROID_HOME o las rutas de apksigner y aapt" >&2; exit 2; }
  latest=$(find "$build_tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
  apksigner=${apksigner:-$latest/apksigner}
  aapt=${aapt:-$latest/aapt}
fi

badging=$($aapt dump badging "$apk" | sed -n '1p')
[[ "$badging" == *"name='cat.tmbagent.elmeuservei.motor'"* ]] || {
  echo "Paquete Android incorrecto" >&2
  exit 3
}

if [[ -n "$expected_version_name" && "$badging" != *"versionName='$expected_version_name'"* ]]; then
  echo "versionName incorrecta. Esperada $expected_version_name" >&2
  exit 5
fi

if [[ -n "$expected_version_code" && "$badging" != *"versionCode='$expected_version_code'"* ]]; then
  echo "versionCode incorrecto. Esperado $expected_version_code" >&2
  exit 6
fi

signature_output=$($apksigner verify --print-certs "$apk" 2>&1)
certificate=$(printf '%s\n' "$signature_output" | awk '
  BEGIN { IGNORECASE = 1 }
  /certificate SHA-256 digest:/ { print tolower($NF); exit }
')
expected=8892fa0f4f446685f24eda202b4ee5493792f064291e2bddd7e33060046b7f56
[[ "$certificate" == "$expected" ]] || {
  echo "Firma incompatible. Esperada $expected y obtenida ${certificate:-ninguna}" >&2
  [[ -n "$certificate" ]] || printf '%s\n' "$signature_output" >&2
  exit 4
}

sha256sum "$apk"
printf '%s\n' "$badging"
printf 'Certificado SHA-256: %s\n' "$certificate"
