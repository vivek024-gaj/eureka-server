# Start with a base image containing Java runtime
# FROM meisterplan/jdk-base:11
FROM adoptopenjdk/openjdk11

# Add Maintainer Info
LABEL maintainer="vivekgajbhiye"

# Add a volume pointing to /tmp
VOLUME /tmp

# Make port 8080 available to the world outside this container
#EXPOSE 8080

# The application's jar file
ARG JAR_FILE=target/eureka-server-1.0.0.jar

# Add the application's jar to the container
ADD ${JAR_FILE} eureka-server-1.0.0.jar

#FONT FIXING
RUN ln -s /lib/libc.musl-x86_64.so.1 /usr/lib/libc.musl-x86_64.so.1
ENV LD_LIBRARY_PATH /usr/lib

ENTRYPOINT ["java","-jar","/eureka-server-1.0.0.jar"]

