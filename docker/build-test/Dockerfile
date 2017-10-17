##########################################
#
# Build container for component: distservice
#
# The purpose of this Docker container is to build a product and run
# its tests.  This container should build the code in an environment 
# that sufficiently matches the target operational environment. 
#
##########################################

# The parent should be an ancestor image of the operational container
# 
FROM maven:3.5-jdk-8-alpine

RUN apk add --no-cache zip git

COPY entrypoint.sh /home/entrypoint.sh

# /home/build is a mount point for the repository's base directory
#
VOLUME /home/build
WORKDIR /home/build

ENTRYPOINT ["/bin/bash", "/home/entrypoint.sh"]

