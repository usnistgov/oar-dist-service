#! /bin/bash
#
# inject_version.sh:  inject current version information into the source code
#
# Usage: inject_setversion.sh
#
# This script can be edited to customize it for its package.
#
# set -x
prog=`basename $0`
execdir=`dirname $0`
[ "$execdir" = "" -o "$execdir" = "." ] && execdir=$PWD
PACKAGE_DIR=`(cd $execdir/.. > /dev/null 2>&1; pwd)`
set -e

[ ! -e "$PACKAGE_DIR/VERSION" ] || \
    cp $PACKAGE_DIR/VERSION $PACKAGE_DIR/src/main/resources

