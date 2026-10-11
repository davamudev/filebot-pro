# syntax=docker/dockerfile:1.7
#
# Headless FileBot Pro for servers and NAS (Synology, QNAP, Unraid, TrueNAS): runs fn:watcher by default, or any other
# command-line operation (fn:amc, -rename, -get-subtitles, ...).
#
#   docker build -t filebot-pro .
#   docker build --secret id=apikeys,src=api-keys.properties -t filebot-pro .   (embed API keys, see README)
#
# Multi-architecture: docker buildx build --platform linux/amd64,linux/arm64 -t filebot-pro .
#
# Desktop app in the browser (noVNC on port 5800): docker build --target gui -t filebot-pro:gui .

# Java bytecode does not depend on the architecture, so the build always runs natively on the build machine
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build

WORKDIR /src

# Gradle wrapper and build scripts first, so the Gradle download is cached between builds
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version

COPY src src
COPY packaging packaging

# build revision (number of git commits), since .git is not part of the build context
ARG REVISION=0

# optional API keys file as build secret. The secret file itself is not stored in a layer, but the keys are compiled into the
# application jar (build.gradle.kts), so an image built with keys contains them: only publish such images if the key terms allow it
RUN --mount=type=secret,id=apikeys,target=/src/api-keys.properties,required=false \
    ./gradlew --no-daemon -Papp.revision=$REVISION installDist


FROM eclipse-temurin:25-jre AS headless

# native tools: MediaInfo (media properties), fpcalc (AcoustID), mkvpropedit and AtomicParsley (--apply metadata), 7-Zip
RUN apt-get update \
    && apt-get install -y --no-install-recommends libmediainfo0v5 libchromaprint-tools mkvtoolnix atomicparsley 7zip \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /src/build/install/filebot-pro /opt/filebot-pro
COPY packaging/docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh /opt/filebot-pro/bin/filebot-pro \
    && mkdir -p /data /input /output

# settings, caches, history and logs live in /data
ENV HOME=/data \
    PUID=1000 \
    PGID=1000 \
    UMASK=022 \
    FILEBOT_PRO_OPTS="-Djava.awt.headless=true -Dapplication.dir=/data -Dapplication.deployment=docker -Duser.home=/data"

VOLUME ["/data"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

# watch /input and move renamed files into /output (Plex naming scheme)
CMD ["-script", "fn:watcher", "/input", "--output", "/output", "--format", "{plex}", "--action", "move", "-rename", "-non-strict"]


# the desktop app on a virtual display, shown in the browser by noVNC: http://<host>:5800
FROM headless AS gui

RUN apt-get update     && apt-get install -y --no-install-recommends xvfb openbox x11vnc novnc websockify        libxext6 libxrender1 libxtst6 libxi6 libxrandr2 fontconfig fonts-dejavu-core fonts-noto-core     && rm -rf /var/lib/apt/lists/*

COPY packaging/docker/gui-entrypoint.sh /usr/local/bin/gui-entrypoint.sh
COPY packaging/docker/novnc-index.html /usr/share/novnc/index.html
RUN chmod +x /usr/local/bin/gui-entrypoint.sh

ENV DISPLAY_WIDTH=1600     DISPLAY_HEIGHT=900     FILEBOT_PRO_OPTS="-Dapplication.dir=/data -Dapplication.deployment=docker -Duser.home=/data -Dsun.java2d.xrender=false"

EXPOSE 5800

ENTRYPOINT ["/usr/local/bin/gui-entrypoint.sh"]
CMD []


# the headless image stays the default target
FROM headless
