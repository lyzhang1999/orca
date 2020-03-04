FROM gcr.io/spinnaker-marketplace/gradle_cache
MAINTAINER delivery-engineering@netflix.com
ENV GRADLE_USER_HOME /gradle_cache/.gradle
COPY . compiled_sources
WORKDIR compiled_sources
RUN ./gradlew --no-daemon orca-web:installDist -x test

FROM openjdk:8-jre-alpine
MAINTAINER delivery-engineering@netflix.com
COPY --from=0 /compiled_sources/orca-web/build/install/orca /opt/orca
COPY --from=0 /compiled_sources/coding-deploy/config /opt/spinnaker/config
COPY --from=0 /compiled_sources/coding-deploy/scripts /opt/spinnaker/scripts
RUN apk --no-cache add --update bash
RUN apk add tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata
RUN adduser -D -S spinnaker
USER spinnaker
CMD ["/opt/orca/bin/orca"]
