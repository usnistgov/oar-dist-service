#!/bin/bash
sudo rm -r /opt/data/backup/oar-dist-service/
if [ -f /home/ubuntu/oar-docker/dist-service/oar-dist-service.jar ];
then
  #backup previous build
  sudo cp -r /home/ubuntu/oar-docker/dist-service/oar-dist-service.jar /opt/data/backup/oar-dist-service/
  #remove previous build
  sudo rm -r /home/ubuntu/oar-docker/dist-service/oar-dist-service.jar
fi
