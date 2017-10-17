#! /bin/bash
#
[ "$1" = "" ] && exec /bin/bash
set -e

case "$1" in
    build)
        scripts/buildall.sh
        ;;
    test)
        scripts/buildall.sh
        scripts/testall.py
        ;;
    bshell)
        scripts/buildall.sh
        exec /bin/bash
        ;;
    ishell)
        scripts/install.sh 
        exec /bin/bash
        ;;
    shell)
        exec /bin/bash
        ;;
    *)
        echo Unknown command: $1
        echo Available commands:  build test install bshell ishell shell
        exit 100
        ;;
esac

EXCODE=$?

# This script must exit with a non-zero value if the build or testing fails
exit $EXCODE
