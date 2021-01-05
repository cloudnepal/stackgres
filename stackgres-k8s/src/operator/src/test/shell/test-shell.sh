#!/bin/sh

set -e

shopt -s expand_aliases 2> /dev/null || true

TEST_SHELL_PATH="${TEST_SHELL_PATH:-$(dirname "$0")}"
PROJECT_PATH="$TEST_SHELL_PATH/../../.."
TARGET_PATH="$PROJECT_PATH/target/shell"

test -f "$PROJECT_PATH/pom.xml"
mkdir -p "$TARGET_PATH"

. "$PROJECT_PATH/src/main/resources/templates/shell-utils"

run_test() {
  TEST_PATH="$1"
  TEST_NAME="$(basename "$(dirname "$TEST_PATH")")/$(basename "$TEST_PATH")"
  TEST_TARGET_PATH="$TARGET_PATH/$TEST_NAME"
  rm -rf "$TEST_TARGET_PATH"
  mkdir -p "$TEST_TARGET_PATH"
  echo
  echo "Running $TEST_NAME..."
  echo
  > "$TEST_TARGET_PATH/log"
  local E_UNSET=true
  local EXIT_CODE
  if echo "$-" | grep -q e
  then
    E_UNSET=false
  fi
  "$E_UNSET" || set +e
  (
    "$SHELL" -l -c $SHELL_XTRACE "$(cat << EOF
TEST_SHELL_PATH="$TEST_SHELL_PATH"
TEST_PATH="$TEST_PATH"
TEST_NAME="$TEST_NAME"
TEST_TARGET_PATH="$TEST_TARGET_PATH"
. "$TEST_SHELL_PATH/test-shell.sh"
. "$TEST_PATH"
test_shell
EOF
      )"
    echo "$?" > "$TEST_TARGET_PATH/exit_code"
  ) 2>&1 | (
      set +x
      while read -r LINE
      do
        printf '%s\n' "$LINE"
        printf '%s\n' "$LINE" >> "$TEST_TARGET_PATH/log"
      done
      )
  EXIT_CODE="$(cat "$TEST_TARGET_PATH/exit_code" | grep '^[0-9]\+$' || echo 1)"
  kill_session_siblings
  "$E_UNSET" || set -e
  if [ "$EXIT_CODE" != 0 ]
  then
    echo
    echo "FAIL $TEST_NAME"
    echo
  else
    echo
    echo "OK $TEST_NAME"
    echo
  fi
  return "$EXIT_CODE"
}

run_all_tests() {
  rm -f "$TARGET_PATH/shell-tests-junit-report.results.xml"
  local START="$(date +%s)"
  local TEST_PATHS="$(ls -1 "$PROJECT_PATH/src/test/shell"/*/[0-9][0-9]-*)"
  local FAIL=false
  local OK_TESTS=""
  local FAIL_TESTS=""
  for TEST_PATH in $TEST_PATHS
  do
    TEST_NAME="$(basename "$(dirname "$TEST_PATH")")/$(basename "$TEST_PATH")"
    TEST_START="$(date +%s)"
    TEST_TARGET_PATH="$TARGET_PATH/$TEST_NAME"
    local E_UNSET=true
    local EXIT_CODE
    if echo "$-" | grep -q e
    then
      E_UNSET=false
    fi
    "$E_UNSET" || set +e
    (
      set -e
      run_test "$TEST_PATH"
    )
    EXIT_CODE="$?"
    "$E_UNSET" || set -e
    if [ "$EXIT_CODE" != 0 ]
    then
      FAIL_TESTS="$FAIL_TESTS $TEST_NAME"
      FAIL=true
      cat << EOF >> "$TARGET_PATH/shell-tests-junit-report.results.xml"
    <testcase classname="$TEST_NAME" name="$TEST_NAME" time="$(($(date +%s) - TEST_START))">
      <failure message="$TEST_NAME failed" type="ERROR">
      <![CDATA[
      $(cat "$TEST_TARGET_PATH/log")
      ]]>
      </failure>
    </testcase>
EOF
    else
      OK_TESTS="$OK_TESTS $TEST_NAME"
      cat << EOF >> "$TARGET_PATH/shell-tests-junit-report.results.xml"
    <testcase classname="$TEST_NAME" name="$TEST_NAME" time="$(($(date +%s) - TEST_START))" />
EOF
    fi
  done

  cat << EOF > "$TARGET_PATH/shell-tests-junit-report.xml"
<?xml version="1.0" encoding="UTF-8"?>
<testsuites time="$(($(date +%s) - START))">
  <testsuite name="shell tests" tests="$(echo "$TESTS" | wc -l)" time="$(($(date +%s) - START))">
$(cat "$TARGET_PATH/shell-tests-junit-report.results.xml")
  </testsuite>
</testsuites>
EOF

  echo
  echo "Results:"
  echo
  echo "$OK_TESTS" | tr ' ' '\n' | grep -v "^$" | sed 's/^\(.*\)$/OK: \1/'
  echo "$FAIL_TESTS" | tr ' ' '\n' | grep -v "^$" | sed 's/^\(.*\)$/FAIL: \1/'
  echo

  if "$FAIL"
  then
    return 1
  fi
}

mock() {
  local MOCK="$(cat << EOF
$1_mocks="$2 \$$1_mocks"
alias $1=$1_mock_entry
$1_mock_entry() {
  local E_UNSET=true
  if echo "\$-" | grep -q e
  then
    E_UNSET=false
  fi
  "\$E_UNSET" || set +e
  local MOKKED="\$TEST_TARGET_PATH/mokked-\$(date +%s)-\$(cat /dev/urandom | tr -dc 'a-z' | head -c 8)"
  not_mokked
  local MOCK
  local EXIT_CODE
  for MOCK in \$$1_mocks
  do
    (
      set -e
      "\$MOCK" "\$@"
    )
    EXIT_CODE="\$?"
    if [ "\$(cat \$MOKKED)" = "true" ]
    then
      break
    fi
  done
  "\$E_UNSET" || set -e
  if [ "\$(cat \$MOKKED)" = "false" ]
  then
    >&2 echo "\$*"
    >&2 echo "Mock missing!"
    exit 1
  fi
  return "\$EXIT_CODE"
}
EOF
  )"
  eval "$MOCK"
}

mokked() {
  echo true > "$MOKKED"
}

not_mokked() {
  echo false > "$MOKKED"
}

if [ "$1" = "all" ]
then
  set_trap

  run_all_tests
elif [ -n "$1" ]
then
  set_trap

  run_test "$1"
else
  "$@"
fi
