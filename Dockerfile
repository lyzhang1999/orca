FROM gcr.io/spinnaker-marketplace/gradle_cache
MAINTAINER delivery-engineering@netflix.com
ENV GRADLE_USER_HOME /gradle_cache/.gradle
COPY . compiled_sources
WORKDIR compiled_sources
RUN ./gradlew --no-daemon orca-web:installDist -x test

FROM openjdk:8-jre-alpine
MAINTAINER delivery-engineering@netflix.com
COPY --from=0 /compiled_sources/orca-web/build/install/orca /opt/orca
RUN apk --no-cache add --update bash
RUN adduser -D -S spinnaker
USER spinnaker
CMD ["/opt/orca/bin/orca"]
