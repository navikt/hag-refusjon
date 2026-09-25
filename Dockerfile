FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
WORKDIR /app
COPY build/install/*/lib /app/lib
ENV JAVA_OPTS='-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp'
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.helsearbeidsgiver.AppKt"]
EXPOSE 8080