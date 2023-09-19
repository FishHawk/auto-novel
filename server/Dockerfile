FROM gradle:7-jdk17 as builder
COPY . /server
WORKDIR /server
RUN gradle --no-daemon installDist

FROM eclipse-temurin:17
COPY --from=builder /server/build/install/wneg /server
ENV LANG C.UTF-8
CMD ["/server/bin/wneg"]