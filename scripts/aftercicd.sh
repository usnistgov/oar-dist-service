#!/bin/bash
cd /home/ubuntu/oar-docker/dist-service
sudo docker rm -f $(sudo docker ps -a | grep "dist-service")
sudo docker rmi -f $(sudo docker images -a | grep "dist-service")
sudo docker-compose up -d --build
