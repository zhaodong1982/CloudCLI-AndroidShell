#!/data/data/com.termux/files/usr/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
apk="$project_dir/output/CloudCLI-Shell.apk"
manifest="$project_dir/app/src/main/AndroidManifest.xml"
sources="$project_dir/app/src/main/java/local/cloudcli/shell"
network_config="$project_dir/app/src/main/res/xml/network_security_config.xml"

test -r "$apk" || { printf 'Missing APK: run ./build.sh first\n' >&2; exit 1; }

apksigner verify "$apk"

badging=$(aapt2 dump badging "$apk")
case "$badging" in
  *"name='local.cloudcli.shell'"*"versionCode='2'"*"versionName='1.0.1'"*"application-label:'CloudCLI'"*) ;;
  *) printf 'Unexpected package, version, or label metadata\n' >&2; exit 1 ;;
esac

permissions=$(aapt2 dump permissions "$apk")
permission_count=$(printf '%s\n' "$permissions" | sed -n 's/^uses-permission: name=//p' | wc -l)
test "$permission_count" -eq 2 || {
  printf 'Unexpected permission count: %s\n' "$permission_count" >&2
  exit 1
}
printf '%s\n' "$permissions" | grep -q "android.permission.INTERNET"
printf '%s\n' "$permissions" | grep -q "android.permission.RECORD_AUDIO"

grep -q 'android:allowBackup="false"' "$manifest"
grep -q 'android:networkSecurityConfig="@xml/network_security_config"' "$manifest"
grep -q 'android:authorities="local.cloudcli.shell.files"' "$manifest"
grep -q 'android:exported="false"' "$manifest"
grep -q '<base-config cleartextTrafficPermitted="false"' "$network_config"
grep -q '<domain includeSubdomains="false">127.0.0.1</domain>' "$network_config"

if grep -R -n -E 'addJavascriptInterface|setAllowFileAccess\(true\)|setAcceptThirdPartyCookies\([^,]+, true\)' "$sources"; then
  printf 'A forbidden WebView capability was found\n' >&2
  exit 1
fi

if grep -R -n 'Intent.ACTION_VIEW' "$sources"; then
  printf 'External browser handoff was found\n' >&2
  exit 1
fi

if grep -R -n 'https://' "$sources"; then
  printf 'A non-local service origin was found\n' >&2
  exit 1
fi
if grep -R -n 'http://' "$sources" | grep -v 'http://127\.0\.0\.1:3001/'; then
  printf 'A non-local cleartext origin was found\n' >&2
  exit 1
fi

grep -q 'WebView.setWebContentsDebuggingEnabled(false)' "$sources/MainActivity.java"
grep -q 'setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)' "$sources/MainActivity.java"
grep -q 'setCacheMode(WebSettings.LOAD_NO_CACHE)' "$sources/MainActivity.java"
grep -q 'setSafeBrowsingEnabled(true)' "$sources/MainActivity.java"
grep -q 'shouldInterceptRequest' "$sources/MainActivity.java"
grep -q 'blockedWebResource' "$sources/MainActivity.java"
grep -q 'DeviceProfileStore.sameOrigin' "$sources/MainActivity.java"
grep -q 'LOCAL_URL = "http://127.0.0.1:3001/"' "$sources/DeviceProfileStore.java"
grep -q 'CloudCLI Shell 仅支持本机服务' "$sources/DeviceProfileStore.java"
grep -q 'blockingHorizontalGesture' "$sources/GestureSafeWebView.java"
grep -q 'ACTION_CANCEL' "$sources/GestureSafeWebView.java"

if grep -q -E 'restoreState\(|saveState\(|\.goBack\(' "$sources/MainActivity.java"; then
  printf 'Browser history or stale WebView state restoration was found\n' >&2
  exit 1
fi

grep -q '<color name="launcher_background">#3478F6</color>' "$project_dir/app/src/main/res/values/colors.xml"
test ! -e "$project_dir/app/src/main/res/drawable-nodpi/cloudcli_icon_art.png" || {
  printf 'Rejected bitmap launcher artwork is still packaged\n' >&2
  exit 1
}
if grep -R -n -E 'cloudcli_icon_art|gradient|strokeColor="#22D3EE"' \
    "$project_dir/app/src/main/res/drawable" "$project_dir/app/src/main/res/mipmap-anydpi-v26"; then
  printf 'Legacy glossy launcher artwork is still referenced\n' >&2
  exit 1
fi

printf 'Verified CloudCLI Shell 1.0.1: signed, loopback-only, flat adaptive icon, cache-fresh, gesture-safe, two permissions, no JS bridge.\n'
