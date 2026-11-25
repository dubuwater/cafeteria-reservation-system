# 배포용 이미지 (Render 등 Docker 지원 호스팅)
# 1단계: Gradle Wrapper로 실행 jar 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: 실행에 필요한 JRE와 jar만 담는다
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul \
    SPRING_PROFILES_ACTIVE=render \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
COPY --from=build /app/build/libs/cafeteria.jar app.jar
RUN useradd --system --uid 1001 app
USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
