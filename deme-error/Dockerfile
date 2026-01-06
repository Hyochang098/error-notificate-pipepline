FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x gradlew && ./gradlew clean bootJar

# 주의: 일부 플랫폼에서 alpine 변종 매니페스트가 없어 빌드 실패할 수 있어
# multi-arch 지원이 넓은 jammy 기반 이미지를 사용
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
# jammy(ubuntu 계열)에서는 addgroup/adduser 옵션이 달라 안전한 system 계정 생성 방식 사용
RUN groupadd -r app && useradd -r -g app app
# 로그 디렉터리 권한 준비
RUN mkdir -p /logs && chown -R app:app /logs
USER app
COPY --from=build /app/build/libs/*.jar app.jar
VOLUME ["/logs"]
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

