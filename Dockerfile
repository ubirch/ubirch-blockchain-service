FROM ubirch/java
ARG JAR_LIBS
ARG JAR_FILE
ARG VERSION
ARG BUILD
ARG SERVICE_NAME

LABEL "com.ubirch.service"="${SERVICE_NAME}"
LABEL "com.ubirch.version"="${VERSION}"
LABEL "com.ubirch.build"="${BUILD}"

EXPOSE 9010
EXPOSE 9020
EXPOSE 4321

# This config object
# There are 3 supported blockchains
# ethereum, ethereum-classic, iota
# At this moment, they share the same configs.
ENV BLOCKCHAIN "ethereum"

ENTRYPOINT \
  /usr/bin/java \
  "-XX:MaxRAM=$(($(cat /sys/fs/cgroup/memory/memory.limit_in_bytes) * 95 / 100 ))"  \
  "-XX:MaxRAMFraction=1" \
  "-Djava.awt.headless=true" \
  "-Djava.security.egd=file:/dev/./urandom" \
  "-Djava.rmi.server.hostname=localhost" \
  "-Dcom.sun.management.jmxremote" \
  "-Dcom.sun.management.jmxremote.port=9010" \
  "-Dcom.sun.management.jmxremote.rmi.port=9010" \
  "-Dcom.sun.management.jmxremote.local.only=false" \
  "-Dcom.sun.management.jmxremote.authenticate=false" \
  "-Dcom.sun.management.jmxremote.ssl=false" \
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=9020" \
  "-Dconfig.resource=application-${BLOCKCHAIN}-docker.conf" \
  "-Dlogback.configurationFile=logback-docker.xml" \
  -jar /usr/share/service/main.jar

# Add Maven dependencies (not shaded into the artifact; Docker-cached)
COPY ${JAR_LIBS} /usr/share/service/lib
# Add the service itself
COPY ${JAR_FILE} /usr/share/service/main.jar
