#! /bin/bash
#
# This provides the command-line for building build-test container
#
prog=`basename $0`
execdir=`dirname $0`
[ "$execdir" = "" -o "$execdir" = "." ] && execdir=$PWD
codedir=`(cd $execdir/.. > /dev/null 2>&1; pwd)`
set -e

PACKAGE_NAME=oar-dist-service

cd $execdir
set -x
docker build -t $PACKAGE_NAME/build-test build-test 2>&1 | tee -a buildcon.log

