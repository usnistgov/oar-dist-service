#FROM eclipse-temurin:8-jdk-focal 
FROM eclipse-temurin:21-jdk

RUN mkdir -p /usr/share/man/man1
RUN apt-get update && apt-get install -y netcat-openbsd zip git less \
                                            ca-certificates python3 curl maven gnupg
RUN cd /usr/bin && ln -s python3 python

COPY cacerts/README.md cacerts/*.crt /usr/local/share/ca-certificates/
RUN update-ca-certificates
RUN java_certs=$JAVA_HOME/lib/security/cacerts; \
    add_certs=`ls /usr/local/share/ca-certificates/*.crt` && \
    for crt in $add_certs; do \
        name=`basename -s .crt $crt`; \
        echo -n ${name}: " "; \
        keytool -import -keystore $java_certs -trustcacerts -file $crt \
                -storepass changeit -alias $name -noprompt; \
    done;
FROM eclipse-temurin:8

RUN mkdir -p /usr/share/man/man1
RUN apt-get update && apt-get install -y netcat-openbsd zip git less \
                                            python2 curl maven
RUN cd /usr/bin && ln -s python2 python

# Create the user that build/test operations should run as.  Normally,
# this is set to match identity information of the host user that is
# launching the container.
#
RUN sed --in-place -e '/CREATE_MAIL_SPOOL/ s/=yes/=no/' /etc/default/useradd
ARG devuser=developer
ARG devuid=1000
RUN grep -qs :${devuid}: /etc/group || \
    groupadd --gid $devuid $devuser
RUN grep -qs ":${devuid}:[[:digit:]]+:" /etc/passwd || \
    useradd -m --comment "OAR Developer" --shell /bin/bash \
            --gid $devuid --uid $devuid $devuser
RUN mkdir /home/$devuser/.m2

VOLUME /app/dev
VOLUME /app/dist
COPY settings.xml /app/mvn-user-settings.xml
COPY settings.xml /home/$devuser/.m2/settings.xml
RUN chown $devuser:$devuser /home/$devuser/.m2/settings.xml && \
    chmod a+r /home/$devuser/.m2/settings.xml
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod a+rx /app/entrypoint.sh

ENV S3MOCK_JAVA_OPTS -XX:+UseContainerSupport 
WORKDIR /app/dev
USER $devuser

ENTRYPOINT [ "/app/entrypoint.sh" ]


