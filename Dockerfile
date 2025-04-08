FROM gradle:jdk17 as builder
COPY * /home/gradle
WORKDIR /home/gradle
RUN gradle build

FROM scratch as out
COPY --from=builder /home/gradle/build/libs/clipboard-agent.jar ./clipboard-agent.jar


