#!/bin/bash
cd /home/ubuntu/oar-docker/dist-service
if [[ $(sudo docker ps -a | grep "dist-service") ]]; then
    sudo docker rm -f $(sudo docker ps -a | grep "dist-service")
fi
if [[ $(sudo docker images -a | grep "dist-service") ]]; then
   sudo docker rmi -f $(sudo docker images -a | grep "dist-service")
fi
sudo docker-compose up -d --build
