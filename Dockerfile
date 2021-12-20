FROM artifactory-principale.fr:9804/alpine-adaje-java:latest

USER root

ARG ARTIFACT_ID="${ARTIFACT_ID}"
ARG POM_VERSION="${POM_VERSION}"

RUN mkdir -p /appli/projects/pdml
RUN mkdir -p /appli/projects/pdml/config
RUN mkdir -p /appli/projects/pdml/home
RUN mkdir -p /var/projects/pdml/logs/appli
RUN mkdir -p /src


WORKDIR /appli/projects/pdml

LABEL fr.stm.integre.maintainer="dsi-donnees-stm-equipe-shapeline@smtp-msg.local"
EXPOSE 8080 5701
ARG JAR_FILE=target/$ARTIFACT_ID-$POM_VERSION.jar
COPY --chown=webadm:webgrp ${JAR_FILE} app.jar
COPY --chown=webadm:webgrp hospital.log config/hospital.log
COPY --chown=webadm:webgrp keystore.jks keystore.jks

RUN chown -R webadm:webgrp /appli/projects/pdml && \
    chown -R webadm:webgrp /var/projects/pdml/logs/appli

USER webadm
ENV LOGGING_PATH="/var/projects/pdml/logs/appli"
ENV STM_HOME="/appli/projects/pdml/home"
ENTRYPOINT ["java","-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:MaxRAMFraction=1", "-Duser.country=FR", "-Duser.language=fr", "-jar","/appli/projects/pdml/app.jar"]

