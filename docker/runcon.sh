#! /bin/bash
#
# run.sh:  execute the build-test docker container
#
# Usage: runcon.sh [cmd]
#
# where cmd is one of:
#  build -- builds package and produces a <pkg>-dist.zip file that contains
#           the package build products
#  test  -- builds and runs unit tests
#  bshell -- builds package, installs it into container, and starts an
#           interactive shell inside the running container
#  shell -- starts an interactive shell inside the running container
#
prog=`basename $0`
execdir=`dirname $0`
[ "$execdir" = "" -o "$execdir" = "." ] && execdir=$PWD
codedir=`(cd $execdir/.. > /dev/null 2>&1; pwd)`

set -e
PACKAGE_NAME=oar-dist-service

cd $execdir
echo "Ensuring container is up to date..." | tee -a runcon.log
buildcon.sh 2>&1 > runcon.log

ti=
(echo "$@" | grep -qs shell) && ti="-ti"

echo '+' docker run $ti --rm -v $codedir:/home/build $PACKAGE_NAME/build-test \
     "$@"  | tee -a runcon.log
exec docker run $ti --rm -v $codedir:/home/build $PACKAGE_NAME/build-test "$@"
