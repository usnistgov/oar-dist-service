#!/bin/bash
cd /home/ubuntu/oar-docker/dist-service
if [[ $(sudo docker ps -aqf "name=dist-service") ]]; then
    sudo docker rm -f $(sudo docker ps -aqf "name=dist-service")
fi
if [[ $(sudo docker images dist-service -aq) ]]; then
   sudo docker rmi -f $(sudo docker images dist-service -aq)
fi
sudo docker-compose up -d --build
