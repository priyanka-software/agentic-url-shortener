#!/bin/sh
set -e
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"; fi
GV=8.10.2; HOME="$DIR/.gradle-bootstrap"; GRADLE="$HOME/gradle-$GV/bin/gradle"
if [ ! -x "$GRADLE" ]; then
 echo "First run: downloading Gradle $GV..."; mkdir -p "$HOME"
 curl -L "https://services.gradle.org/distributions/gradle-$GV-bin.zip" -o "$HOME/gradle.zip"
 unzip -q -o "$HOME/gradle.zip" -d "$HOME"
fi
exec "$GRADLE" "$@"
