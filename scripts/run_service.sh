#! /bin/bash
#
#
# run_service.sh:  launch the data distribution web service
#
set -e
prog=`basename $0`
execdir=`dirname $0`
[ "$execdir" = "" -o "$execdir" = "." ] && execdir=$PWD
PACKAGE_DIR=`(cd $execdir/.. > /dev/null 2>&1; pwd)`
DIST_DIR=$PACKAGE_DIR/dist
targetdir=$PACKAGE_DIR/target

function usage {
    cat <<EOF
NAME
   $prog -- launch the data distribution web service on localhost

SYNOPSIS
   $prog [-p PORT] [-d DIR] [-l FILE]

OPTIONS
   -l FILE, --logfile=FILE   write the log to the given file
   
   -d DIR, --data-dir=DIR    take DIR as the location of bag files on local disk

   -p NUM, --port=NUM        launch the service on port NUM (default:  8083)
   
   -Dparam=value             pass an arbitrary config parameter and value to the application

   -h, --help                print this message and exit

EOF
}

PROP_ARGS=

# handle command line options
while [ "$1" != "" ]; do 
  case "$1" in
    --logfile=*)
        LOGFILE=`echo $1 | sed -e 's/[^=]*=//'`
        ;;
    --logfile|-l)
        shift
        LOGFILE=$1
        ;;
    --data-dir=*)
        DATA_DIR=`echo $1 | sed -e 's/[^=]*=//'`
        ;;
    --data-dir|-d)
        shift
        DATA_DIR=$1
        ;;
    --port=*)
        PORT=`echo $1 | sed -e 's/[^=]*=//'`
        ;;
    --port|-p)
        shift
        PORT=$1
        ;;
    --echo|-e)
        ECHO_CL_ONLY=1
        ;;
    -D*)
        PROP_ARGS="$PROP_ARGS $1"
        ;;
    --help|-h)
        usage
        exit 0
        ;;
    -*)
        echo "$prog: unsupported option:" $1
        false
        ;;
    *)
        echo "$prog: unsupported argument:" $1
        false
        ;;
  esac
  shift
done

# Defaults
[ -n "$DATA_DIR" ] || DATA_DIR=${PACKAGE_DIR}/src/test/resources
[ -n "$LOGFILE" ] || LOGFILE=${PWD}/distservice.log

# set CL arguments
[ -z "$LOGFILE" ] || PROP_ARGS="$PROP_ARGS -Dlogging.path=$PWD -Dlogging.file=$LOGFILE"
(echo $PROP_ARGS | grep -qs -e '-Ddistrib.bagstore.mode=') || \
    PROP_ARGS="$PROP_ARGS -Ddistrib.bagstore.mode=local"
[ -z "$DATA_DIR" ] || PROP_ARGS="$PROP_ARGS -Ddistrib.bagstore.location=$DATA_DIR"
[ -z "$PORT" ] || PROP_ARGS="$PROP_ARGS -Dserver.port=$PORT"

[ -n "$PORT" ] || PORT=8083
(echo $PROP_ARGS | grep -qs -e '-Ddistrib.baseur=') || \
    PROP_ARGS="$PROP_ARGS -Ddistrib.baseurl=http://localhost:$PORT/oar-dist-service"


echo java -Xmx900m -Xss256k -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=local \
          $PROP_ARGS -jar $targetdir/oar-dist-service.jar
[ -n "$ECHO_CL_ONLY" ] || \
    java -Xmx900m -Xss256k -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=local \
        $PROP_ARGS -jar $targetdir/oar-dist-service.jar

