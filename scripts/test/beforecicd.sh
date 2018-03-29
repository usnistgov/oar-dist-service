#!/bin/bash
if [ -f /home/ubuntu/oar-docker/apps/dist-service/oar-dist-service.jar ];
then
  #backup previous build
  sudo cp -r /home/ubuntu/oar-docker/apps/dist-service/oar-dist-service.jar /opt/data/backup/oar-dist-service/
  #remove previous build
  sudo rm -r /home/ubuntu/oar-docker/apps/dist-service/oar-dist-service.jar
fi
