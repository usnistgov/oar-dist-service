#! /bin/bash
#
# This builds all distributable products that are part of this repository.
#
prog=`basename $0`
execdir=`dirname $0`
[ "$execdir" = "" -o "$execdir" = "." ] && execdir=$PWD
basedir=`(cd $execdir/.. > /dev/null 2>&1; pwd)`
set -e

PACKAGE_NAME=oar-dist-service

echo Test not yet enabled.

