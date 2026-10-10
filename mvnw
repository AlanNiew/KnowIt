#!/bin/sh
# ----------------------------------------------------------------------------
#  KnowIt · 最小可用 Maven Wrapper（script-only，无 maven-wrapper.jar）
#
#  行为：
#    1. 读取 .mvn/wrapper/maven-wrapper.properties 里的 distributionUrl
#    2. 若 MAVEN_USER_HOME/wrapper/dists/ 下尚无该版本，下载并解压
#    3. 用它执行 Maven
#
#  为什么不直接用 Maven Wrapper 插件生成的标准脚本：本仓库的第一个提交
#  是在无法执行 Maven 的环境里完成的，无法跑 `mvn wrapper:wrapper`。
#  本脚本刻意保持简单可读，能覆盖 95% 场景。
#  谁有完整环境，跑一次 `./mvnw -N wrapper:wrapper -Dmaven=3.9.9`
#  用官方脚本替换本文件即可（见 .mvn/wrapper/maven-wrapper.properties 注释）。
# ----------------------------------------------------------------------------
set -eu

MAVEN_PROJECTBASEDIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$PROPERTIES" ]; then
  echo "ERROR: 缺少 $PROPERTIES" >&2
  exit 1
fi

# 从 properties 里取 distributionUrl（数值即可，不做完整 properties 解析）
DIST_URL=$(sed -n 's/^distributionUrl[[:space:]]*=[[:space:]]*//p' "$PROPERTIES" | tr -d '\r' | tail -n 1)
if [ -z "$DIST_URL" ]; then
  echo "ERROR: $PROPERTIES 中没有 distributionUrl" >&2
  exit 1
fi

MAVEN_VERSION=$(basename "$DIST_URL" .zip | sed 's/^apache-maven-//')
DIST_NAME=$(basename "$DIST_URL")

MAVEN_USER_HOME=${MAVEN_USER_HOME:-$HOME/.m2}
DIST_DIR="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$MAVEN_VERSION"
MARKER="$DIST_DIR/.extracted"

if [ ! -f "$MARKER" ]; then
  echo ">> 下载 Maven $MAVEN_VERSION ..."
  mkdir -p "$DIST_DIR/apache-maven-$MAVEN_VERSION"
  TMP="$DIST_DIR/$DIST_NAME.tmp"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$TMP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DIST_URL" -O "$TMP"
  else
    echo "ERROR: 需要 curl 或 wget 下载 Maven" >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$TMP" -d "$DIST_DIR"
  else
    echo "ERROR: 需要 unzip 解压 Maven 发行包" >&2
    exit 1
  fi
  rm -f "$TMP"
  touch "$MARKER"
  echo ">> Maven $MAVEN_VERSION 已就绪"
fi

MVN_BIN="$DIST_DIR/apache-maven-$MAVEN_VERSION/bin/mvn"
if [ ! -x "$MVN_BIN" ]; then
  echo "ERROR: 未找到 $MVN_BIN" >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "WARNING: JAVA_HOME 未设置，使用 PATH 中的 java。" >&2
fi

# 读取 .mvn/jvm.config（官方 Maven Wrapper 的行为之一）。
# 该文件每行一个 JVM 参数，不支持注释。
# 目前用于给 google-java-format 一类需要 JDK 内部 API 的工具补 --add-exports，
# 这样即便将来升级到需要它的版本，用户也不必手动设 MAVEN_OPTS。
JVM_CONFIG="$MAVEN_PROJECTBASEDIR/.mvn/jvm.config"
if [ -f "$JVM_CONFIG" ]; then
  JVM_CONFIG_ARGS=$(tr '\r\n' '  ' < "$JVM_CONFIG")
  MAVEN_OPTS="$JVM_CONFIG_ARGS ${MAVEN_OPTS:-}"
  export MAVEN_OPTS
fi

# shellcheck disable=SC2086
exec "$MVN_BIN" -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" ${MAVEN_OPTS:-} "$@"
