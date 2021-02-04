#!/bin/sh
echo CWD=`pwd`
echo java  -Xmx128m -Djava.security.egd=file:/dev/./urandom -jar s3mock-2.1.19.jar
exec java  -Xmx128m -Djava.security.egd=file:/dev/./urandom -jar s3mock-2.1.19.jar
