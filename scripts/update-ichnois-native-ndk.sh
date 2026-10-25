#!/usr/bin/env bash
set -euo pipefail

cd $(dirname "$0")/../
GRADLE_NDK_FILEPATH=gradle/libs.versions.toml

case $1 in
get-version)
    perl -ne 'print "$1\n" if ( m/module = "io\.ichnois:ichnois-native-ndk", version = "([0-9.]+)"/ )' "$GRADLE_NDK_FILEPATH"
    ;;
get-repo)
    echo "https://github.com/getsentry/ichnois-native.git"
    ;;
set-version)
    version=$2

    echo "Setting ichnois-native-ndk version to '$version'"

    PATTERN='(module = "io\.ichnois:ichnois-native-ndk", version = ")[0-9.]+(")'
    perl -pi -e "s/$PATTERN/\${1}$version\${2}/" "$GRADLE_NDK_FILEPATH"
    ;;
*)
    echo "Unknown argument $1"
    exit 1
    ;;
esac
