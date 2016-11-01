FROM java:8
RUN  apt-get clean && apt-get update && apt-get install -y netcat
RUN mkdir -p /usr/local/oar-dist-service
ADD oar-dist-service-1.0.0-SNAPSHOT.jar /usr/local/oar-dist-service/
ADD run.sh run.sh
RUN chmod +x run.sh
CMD ./run.sh
