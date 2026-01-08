# 에러 알림 파이프라인 (Error Notification Pipeline)

서버에서 발생한 에러를 **Discord 웹훅**을 통해 실시간으로 알림 받는 모니터링 파이프라인입니다.

---

## 개요

서비스를 운영하다 보면 운영 서버는 물론, 개발·테스트 단계의 배포 서버에서도 다양한 에러 상황을 마주하게 된다.

프론트엔드에서의 API 연동 과정이나 실제 사용자가 배포 서버를 사용하는 중에도 예외가 발생할 수 있다.

이러한 문제가 발생했을 때, 빠르게 인지하고 대응하기 위해 대부분의 서비스 기업들은 모니터링 도구를 도입한다.

모니터링 도구는 시스템 상태를 시각화하거나 에러를 사전에 예측하는 기능을 제공하기도 하지만, 가장 보편적인 목적은 **에러 발생 시 운영 측에 즉각적으로 알림을 전달하는 것**이다.

에러 예측까지는 아니더라도, 에러가 발생했을 때 알림을 전달하는 정도의 기능은 비교적 간단하게 직접 구축할 수도 있다.

대부분의 메신저는 **웹훅(Webhook)** 연동을 지원한다. 웹훅이란 특정 조건이 만족되었을 때, 미리 정의된 URL로 HTTP 요청을 보내는 방식의 알림 메커니즘이다.

이번 문서에서는 **디스코드 웹훅**을 활용하여 서버에서 발생한 에러를 메신저로 전달하는 **에러 알림 파이프라인**을 구축하는 방법을 정리해본다.

이 구조를 활용하면 무중단 배포가 구축되지 않은 환경에서도 CI/CD 진행 상황이나 서버 에러 상황을 메신저로 공유할 수 있다.

더 나아가 웹훅을 직접 지원하지 않는 메신저(예: 카카오톡)의 경우에도, 카카오 API를 활용해 단체 채팅방 알림으로 확장하는 것이 가능하다.

---

## 환경 및 시나리오

### 1. 환경

| 구성 요소 | 기술 스택 | 역할 |
|----------|----------|------|
| 서버 | Spring Boot 3.5.x | 애플리케이션 서버 |
| 모니터링 | Grafana + Loki + Prometheus | 로그/메트릭 수집 및 알림 |
| 메신저 | Discord | 알림 수신 |

### 2. 테스트 시나리오

1. 로컬 PC에서 Docker를 사용해 Spring, Grafana, Loki로 모니터링 환경을 구성한다.
2. 의도적으로 에러를 발생시키는 API를 생성한다.
3. 에러 발생 시, 연결된 Discord 채널로 알림이 전달되는 것을 확인한다.

---

## 아키텍처

```
┌─────────────┐     ┌───────────┐     ┌──────────┐     ┌─────────────┐     ┌─────────┐
│   Spring    │────▶│  Promtail │────▶│   Loki   │────▶│   Grafana   │────▶│ Discord │
│   (로그)    │     │  (수집)   │     │  (저장)  │     │  (알림)     │     │ (수신)  │
└─────────────┘     └───────────┘     └──────────┘     └─────────────┘     └─────────┘
       │
       ▼
┌─────────────┐
│ Prometheus  │ (메트릭 수집)
└─────────────┘
```

---

## 설정 방법

### 핵심 개념

#### 1. Spring Actuator

Grafana가 수집하는 Loki와 Prometheus가 서버에 접근할 수 있게 해주는 라이브러리이다.

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

> 현재는 Security 설정이 되어있지 않아 별도의 CSRF 설정이 필요 없다.

#### 2. application.yml과 docker-compose.yml의 연관관계

- **application.yml**: 로그 파일을 특정 폴더(`/logs/app.log`)에 저장하도록 설정
- **docker-compose.yml**: Promtail이 해당 로그 폴더를 바라보도록 볼륨 마운트 설정

```yaml
# application.yml - 로그 저장 경로 설정
logging:
  file:
    name: /logs/app.log
```

```yaml
# docker-compose.yml - 볼륨 공유
volumes:
  - spring-logs:/logs  # Spring과 Promtail이 같은 볼륨 공유
```

### 설정 단계

1. **Prometheus와 Loki 설정**: 원하는 이벤트(에러 로그, 메트릭 등) 지정
2. **Discord Webhook 연결**: 채널에서 Webhook URL 복사 후 Grafana Contact Point에 등록

---

## 프로젝트 구조

```
deme-error/
├── src/
│   └── main/
│       ├── java/.../api/
│       │   └── ErrorTestController.java  # 에러 테스트 API
│       └── resources/
│           └── application.yml           # Spring 설정 (로그 경로)
├── docker-compose.yml                    # 컨테이너 구성 (볼륨 마운트)
├── Dockerfile                            # Spring 이미지 빌드
├── loki-config.yaml                      # Loki 저장소 설정
├── promtail-config.yaml                  # Promtail 로그 수집 설정
├── prometheus.yml                        # Prometheus 메트릭 스크랩 설정
├── .env                                  # 환경변수 (Webhook URL 등)
└── README.md
```

---

## 설치 및 실행

### 1. 사전 요구사항

- Docker Desktop 설치
- Java 17+
- Discord Webhook URL

### 2. 환경변수 설정

프로젝트 루트에 `.env` 파일 생성:

```env
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN
```

> ⚠️ `.env` 파일은 `.gitignore`에 추가하여 저장소에 노출되지 않도록 한다.

### 3. 컨테이너 실행

```bash
# 빌드 및 실행
docker compose build
docker compose up -d

# 상태 확인
docker compose ps -a
```

### 4. 접속 정보

| 서비스 | URL | 비고 |
|--------|-----|------|
| Spring | http://localhost:8080 | API 서버 |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | 메트릭 조회 |
| Loki | http://localhost:3100 | 로그 저장소 |

---

## API 엔드포인트

### 애플리케이션 API

```bash
# 헬스 체크
curl http://localhost:8080/api/health

# 에러 발생 테스트 (의도적 예외 발생)
curl http://localhost:8080/api/error-test
```

### Actuator 엔드포인트

```bash
# 상태 확인
curl http://localhost:8080/actuator/health

# Prometheus 메트릭
curl http://localhost:8080/actuator/prometheus
```

---

## Grafana 설정 가이드

### Step 1. Loki 데이터소스 추가

1. Grafana 접속 (http://localhost:3000)
2. **Connections → Data sources → Add data source**
3. **Loki** 선택
4. URL: `http://loki:3100`
5. **Save & Test** → "Data source connected" 확인

### Step 2. Contact Point 생성 (Discord Webhook)

1. **Alerting → Contact points → Add contact point**
2. Name: `discord-alerts`
3. Integration: **Discord**
4. Webhook URL: Discord에서 복사한 Webhook URL 입력
5. **Test** 버튼으로 테스트 메시지 확인
6. **Save**

### Step 3. Alert Rule 생성

1. **Alerting → Alert rules → New alert rule**
2. 설정:
   - Rule name: `error-log-alert`
   - Data source: **Loki**
   - 쿼리:
     ```
     count_over_time({job="spring"} |= "Exception" [1m])
     ```
   - Condition: `A > 0`
   - Evaluate every: `1m`
   - For: `0s`
3. Folder/Group 지정
4. **Save and exit**

### Step 4. Notification Policy 연결

1. **Alerting → Notification policies**
2. Default policy 편집
3. Contact point: `discord-alerts` 선택
4. **Save**

---

## 테스트 시나리오

```bash
# 1. 에러 발생
curl http://localhost:8080/api/error-test

# 2. Grafana Explore에서 로그 확인
#    쿼리: {job="spring"} |= "Exception"

# 3. 1분 후 Alert 상태 확인
#    Alerting → Alert rules → Firing 상태 확인

# 4. Discord 채널에서 알림 수신 확인
```

---

## 트러블슈팅

### 알림이 오지 않을 때 체크리스트

| 단계 | 확인 항목 | 확인 방법 |
|------|----------|----------|
| 1 | Loki 정상 기동 | `curl http://localhost:3100/ready` |
| 2 | Promtail 로그 전송 | `docker compose logs promtail` |
| 3 | 데이터소스 연결 | Grafana → Data sources → Test |
| 4 | 로그 수집 확인 | Grafana Explore → `{job="spring"}` |
| 5 | Contact Point 설정 | Alerting → Contact points → Test |
| 6 | Alert Rule 생성 | Alerting → Alert rules |
| 7 | Notification Policy | Alerting → Notification policies |
| 8 | Alert 상태 | Firing 상태인지 확인 |

### Loki 컨테이너가 종료될 때

```bash
# 로그 확인
docker compose logs --tail=100 loki

# 볼륨 초기화 후 재시작
docker compose down
docker volume rm deme-error_loki-data deme-error_loki-wal
docker compose up -d
```

### Grafana 알림 로그 확인

```bash
docker compose logs --tail=100 grafana | grep -i "alert\|discord\|webhook"
```

---

## 유용한 명령어

```bash
# 전체 컨테이너 상태
docker compose ps -a

# 특정 서비스 로그
docker compose logs --tail=50 spring
docker compose logs --tail=50 loki
docker compose logs -f promtail

# 컨테이너 재시작
docker compose restart loki

# 전체 중지 및 삭제
docker compose down

# 볼륨 포함 삭제
docker compose down -v
```

---

## 확장 가능성

- **Slack/카카오톡**: Grafana Contact Point에서 다른 메신저 연동 가능
- **CI/CD 알림**: GitHub Actions/Jenkins 빌드 결과를 동일 Webhook으로 전송
- **대시보드**: Grafana에서 에러 발생 추이, 응답 시간 등 시각화

---

## 참고

- [Grafana Alerting 공식 문서](https://grafana.com/docs/grafana/latest/alerting/)
- [Loki 공식 문서](https://grafana.com/docs/loki/latest/)
- [Discord Webhook 가이드](https://discord.com/developers/docs/resources/webhook)
