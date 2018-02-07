#! /bin/bash
#
[ "$1" = "" ] && exec /bin/bash

case "$1" in
    makedist)
        scripts/makedist
        ;;
    testall)
        scripts/testall
        ;;
    shell)
        exec /bin/bash
        ;;
    *)
        echo Unknown command: $1
        echo Available commands makedist testall shell
        ;;
esac

