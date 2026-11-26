#!/usr/bin/env bash
set -euo pipefail

: "${MOTOR_SIGNING_STORE_FILE:?Falta MOTOR_SIGNING_STORE_FILE}"
: "${MOTOR_SIGNING_STORE_PASSWORD:?Falta MOTOR_SIGNING_STORE_PASSWORD}"
: "${MOTOR_SIGNING_KEY_ALIAS:?Falta MOTOR_SIGNING_KEY_ALIAS}"
: "${MOTOR_SIGNING_KEY_PASSWORD:?Falta MOTOR_SIGNING_KEY_PASSWORD}"

gradle_cmd=${GRADLE_CMD:-gradle}
root=$(cd "$(dirname "$0")/.." && pwd)
project="$root/miralin-corporatiu-android"

"$gradle_cmd" -p "$project" clean :app:testDebugUnitTest :app:assembleDebug --stacktrace

version=$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" "$project/app/build.gradle")
output="$root/artifacts/el-meu-servei-motor/$version"
mkdir -p "$output"
cp "$project/app/build/outputs/apk/debug/app-debug.apk" \
  "$output/El-meu-servei-motor-$version.apk"

"$root/scripts/verify-el-meu-servei-motor.sh" \
  "$output/El-meu-servei-motor-$version.apk"
