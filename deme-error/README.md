# 에러 알림 파이프라인 (Error Notification Pipeline)

서버에서 발생한 에러를 **Discord 웹훅**을 통해 실시간으로 알림 받는 모니터링 파이프라인입니다.

## 개요

서비스 운영 중 발생하는 에러를 빠르게 인지하고 대응하기 위해 구축한 알림 시스템입니다.

- **Spring Boot** 애플리케이션에서 에러 발생
- **Promtail**이 로그를 수집하여 **Loki**로 전송
- **Grafana**가 Loki 로그를 모니터링하고 조건 충족 시 **Discord**로 알림 발송

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

## 기술 스택

| 구성 요소 | 버전 | 역할 |
|----------|------|------|
| Spring Boot | 3.5.x | 애플리케이션 서버 |
| Grafana | 10.4.2 | 대시보드 & 알림 |
| Loki | 2.9.3 | 로그 저장소 |
| Promtail | 2.9.3 | 로그 수집 에이전트 |
| Prometheus | 2.52.0 | 메트릭 수집 |
| Docker Compose | - | 컨테이너 오케스트레이션 |

## 프로젝트 구조

```
deme-error/
├── src/
│   └── main/
│       ├── java/.../api/
│       │   └── ErrorTestController.java  # 에러 테스트 API
│       └── resources/
│           └── application.yml           # Spring 설정
├── docker-compose.yml                    # 컨테이너 구성
├── Dockerfile                            # Spring 이미지 빌드
├── loki-config.yaml                      # Loki 설정
├── promtail-config.yaml                  # Promtail 설정
├── prometheus.yml                        # Prometheus 설정
├── .env                                  # 환경변수 (생성 필요)
└── README.md
```

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

## Grafana 설정 가이드

### 1. Loki 데이터소스 추가

1. Grafana 접속 (http://localhost:3000)
2. **Connections → Data sources → Add data source**
3. **Loki** 선택
4. URL: `http://loki:3100`
5. **Save & Test**

### 2. Contact Point 생성 (Discord)

1. **Alerting → Contact points → Add contact point**
2. Name: `discord-alerts`
3. Integration: **Discord**
4. Webhook URL: Discord Webhook URL 입력
5. **Test** 버튼으로 테스트 메시지 확인
6. **Save**

### 3. Alert Rule 생성

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
3. **Save and exit**

### 4. Notification Policy 연결

1. **Alerting → Notification policies**
2. Default policy 편집
3. Contact point: `discord-alerts` 선택
4. **Save**

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

## 확장 가능성

- **Slack/카카오톡**: Grafana Contact Point에서 다른 메신저 연동 가능
- **CI/CD 알림**: GitHub Actions/Jenkins 빌드 결과를 동일 Webhook으로 전송
- **대시보드**: Grafana에서 에러 발생 추이, 응답 시간 등 시각화

## 참고

- [Grafana Alerting 공식 문서](https://grafana.com/docs/grafana/latest/alerting/)
- [Loki 공식 문서](https://grafana.com/docs/loki/latest/)
- [Discord Webhook 가이드](https://discord.com/developers/docs/resources/webhook)

