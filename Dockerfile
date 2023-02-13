FROM docker.io/eclipse-temurin:11-jre-jammy

ENV TEST_ROOT="/data"
ENV FILE_COUNT="4"
ENV FILE_SIZE="64"
ENV MAX_FILES_PER_THREAD="30000"
ENV THREAD_COUNT="10"

VOLUME /data

COPY target/ocfl-azure-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar /opt/ocfl-azure-test.jar

WORKDIR /opt

ENTRYPOINT ["java", "-jar", "ocfl-azure-test.jar"]
