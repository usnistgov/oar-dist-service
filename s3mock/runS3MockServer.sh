#!/bin/sh
echo CWD=`pwd`
echo java $S3MOCK_JAVA_OPTS -Xmx128m -Djava.security.egd=file:/dev/./urandom -jar s3mock-2.1.19.jar
exec java $S3MOCK_JAVA_OPTS -Xmx128m -Djava.security.egd=file:/dev/./urandom -jar s3mock-2.1.19.jar
