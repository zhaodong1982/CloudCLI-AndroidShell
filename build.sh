#!/data/data/com.termux/files/usr/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
data_home=${XDG_DATA_HOME:-"$HOME/.local/share"}
android_jar=${ANDROID_JAR:-"$HOME/.local/share/android-sdk/android-35/android.jar"}
manifest="$project_dir/app/src/main/AndroidManifest.xml"
res_dir="$project_dir/app/src/main/res"
java_dir="$project_dir/app/src/main/java"
build_dir="$project_dir/build/apk"
output_dir="$project_dir/output"
signing_dir=${CLOUDCLI_SHELL_SIGNING_DIR:-"$data_home/cloudcli-shell"}
keystore="$signing_dir/release.jks"
password_file="$signing_dir/keystore-password"

test -r "$android_jar" || { printf 'Missing Android platform jar: %s\n' "$android_jar" >&2; exit 1; }

rm -rf "$build_dir"
mkdir -p "$build_dir/gen" "$build_dir/classes" "$build_dir/dex" "$output_dir" "$signing_dir"

aapt2 compile --dir "$res_dir" -o "$build_dir/resources.zip"
aapt2 link \
  -o "$build_dir/unsigned.apk" \
  -I "$android_jar" \
  --manifest "$manifest" \
  --java "$build_dir/gen" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 2 \
  --version-name 1.0.1 \
  --auto-add-overlay \
  "$build_dir/resources.zip"

find "$java_dir" "$build_dir/gen" -name '*.java' -print > "$build_dir/java-sources.txt"
javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -bootclasspath "$android_jar" \
  -d "$build_dir/classes" @"$build_dir/java-sources.txt"
jar --create --file "$build_dir/classes.jar" -C "$build_dir/classes" .
d8 --lib "$android_jar" --min-api 23 --output "$build_dir/dex" "$build_dir/classes.jar"
jar --update --file "$build_dir/unsigned.apk" -C "$build_dir/dex" classes.dex

if [ ! -f "$keystore" ]; then
  umask 077
  head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' > "$password_file"
  signing_password=$(sed -n '1p' "$password_file")
  keytool -genkeypair -noprompt \
    -keystore "$keystore" \
    -storepass "$signing_password" \
    -keypass "$signing_password" \
    -alias cloudcli-local \
    -keyalg RSA \
    -keysize 3072 \
    -validity 10000 \
    -dname 'CN=CloudCLI Local, OU=Local Build, O=CloudCLI, C=CN'
fi

signing_password=$(sed -n '1p' "$password_file")
apksigner sign \
  --ks "$keystore" \
  --ks-key-alias cloudcli-local \
  --ks-pass "pass:$signing_password" \
  --key-pass "pass:$signing_password" \
  --out "$output_dir/CloudCLI-Shell.apk" \
  "$build_dir/unsigned.apk"

apksigner verify --verbose --print-certs "$output_dir/CloudCLI-Shell.apk"
printf 'Built %s\n' "$output_dir/CloudCLI-Shell.apk"
