#!/bin/bash
cd /home/ubuntu/oar-docker/apps/
if [[ $(sudo docker ps -aqf "name=dist-service") ]]; then
    sudo docker rm -f $(sudo docker ps -aqf "name=dist-service")
fi
if [[ $(sudo docker images dist-service -aq) ]]; then
   sudo docker rmi -f $(sudo docker images dist-service -aq)
fi

sudo docker-compose -f docker-compose.yml -f docker-compose.test.yml up -d --build
