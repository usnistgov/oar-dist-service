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
CON_USER=`id --user --name`
CON_UID=`id --user`

cd $execdir
set -x
docker build -t $PACKAGE_NAME/build-test build-test \
             --build-arg=user=$CON_USER --build-arg=uid=$CON_UID 2>&1 \
    | tee -a buildcon.log

