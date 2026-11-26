#!/usr/bin/env bash
set -euo pipefail

apk=${1:?"Uso: verify-el-meu-servei-motor.sh APK [APKSIGNER] [AAPT]"}
apksigner=${2:-${APKSIGNER:-}}
aapt=${3:-${AAPT:-}}

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

certificate=$($apksigner verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
expected=8892fa0f4f446685f24eda202b4ee5493792f064291e2bddd7e33060046b7f56
[[ "$certificate" == "$expected" ]] || {
  echo "Firma incompatible. Esperada $expected y obtenida ${certificate:-ninguna}" >&2
  exit 4
}

sha256sum "$apk"
printf '%s\n' "$badging"
printf 'Certificado SHA-256: %s\n' "$certificate"
