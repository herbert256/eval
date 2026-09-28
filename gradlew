#!/bin/sh
# Gradle wrapper script for UNIX systems

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

die() { echo "$*" 1>&2; exit 1; }

GRADLE_HOME="${GRADLE_HOME:-$HOME/.gradle}"

# Find java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Determine project directory
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")/"$link"
    fi
done
PRGDIR=$(dirname "$PRG")
cd "$PRGDIR" || exit 1
APP_HOME=$(pwd -P)

# Add wrapper jar to classpath
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download wrapper if needed, and only use it when its checksum matches Gradle's published one
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHA256="d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -fL -o "$WRAPPER_JAR.tmp" "https://services.gradle.org/distributions/gradle-8.5-wrapper.jar" || die "Could not download the Gradle wrapper"
    if command -v sha256sum > /dev/null; then
        ACTUAL_SHA256=$(sha256sum "$WRAPPER_JAR.tmp" | cut -d' ' -f1)
    else
        ACTUAL_SHA256=$(shasum -a 256 "$WRAPPER_JAR.tmp" | cut -d' ' -f1)
    fi
    [ "$ACTUAL_SHA256" = "$WRAPPER_SHA256" ] || { rm -f "$WRAPPER_JAR.tmp"; die "Gradle wrapper checksum mismatch"; }
    mv "$WRAPPER_JAR.tmp" "$WRAPPER_JAR"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
