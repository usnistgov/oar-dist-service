#! /bin/bash
#
prog=`basename $0`
execdir=`dirname $0`
[ "$execdir" = "" -o "$execdir" = "." ] && execdir=$PWD
codedir=`(cd $execdir/.. > /dev/null 2>&1; pwd)`

set -e

PACKAGE_NAME=oar-dist-service
image=build-test

(docker images | grep -qs $PACKAGE_NAME/$image) || {
    echo "${prog}: Docker image $image not found; building now..."
    echo '+' $execdir/dockbuild.sh -q
    $execdir/dockbuild.sh -q || {
        echo "${prog}: Failed to build docker containers; see" \
             "docker/dockbuild.logfor details."
        false
    }
}

ti=
(echo "$@" | grep -qs shell) && ti="-ti"

echo docker run $ti --rm -v $codedir:/app/dev $PACKAGE_NAME/$image "$@"
exec docker run $ti --rm -v $codedir:/app/dev $PACKAGE_NAME/$image "$@"



