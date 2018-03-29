#!/bin/bash
if [ -f /home/ubuntu/oar-docker/apps/dist-service/oar-dist-service.jar ];
then
   #remove previous build
   sudo rm -r /home/ubuntu/oar-docker/apps/dist-service/oar-dist-service.jar
fi
