# AWS 배포 런북 — EC2 + RDS (시드니)

수집 서버를 EC2 1대에 상시 띄우고, 매일 KST 09:00(실패 시 15:00 재시도)에 `dailyCollectJob` 을 돌린다.
결정 배경은 [ADR-0006](../adr/0006-ec2-rds-hosting.md).

```
main 머지 → GitHub Actions(release.yml) → GitHub Release build-<sha>
                                              ▲ 10분마다 확인 (gongsi-update.timer)
EC2 t4g.micro (퍼블릭 서브넷, 인바운드 0) ──5432──▶ RDS PostgreSQL 16 (프라이빗 서브넷)
비밀값: SSM Parameter Store /gongsi/*  → 기동 시 run.sh 가 읽음
```

프로젝트(Blank Slate) 계정 제약 — 2026-10-30 확인
- 리전은 **아시아 태평양(시드니) ap-southeast-2** 만 쓸 수 있다. 콘솔 헤더에 리전 메뉴가 없다
- OIDC 공급자·RDS 백업 7일 등은 '고급 기능 활성화'/'플랜 업그레이드'가 필요하다 — 누르지 않는다
- 계정 ID `437075213716`, VPC `vpc-05252c3b712ee4362`

> **데이터 보호**: 서버 첫 수집이 그날 15:00 KST 까지 확인되지 않으면 로컬에서 수동 실행한다.
> ```bash
> set -a && . ./.env && set +a && ./gradlew bootRun --args='--spring.main.web-application-type=none --spring.batch.job.enabled=true --spring.batch.job.name=dailyCollectJob targetDate=YYYY-MM-DD'
> ```
> 로컬에서 받은 날짜는 8단계로 RDS 에 옮긴다.

---

## 0. 예산 알림 (선택)

Billing → 예산 → **월별 비용 예산** `gongsi-monthly` 40 USD. 남은 크레딧은 콘솔 오른쪽 위 계정 메뉴에서 본다.

## 1. VPC

**VPC → VPC 생성 → VPC 등**: 이름 `gongsi`, `10.0.0.0/16`, AZ 2, 퍼블릭 2, 프라이빗 2,
NAT **없음**, VPC 엔드포인트 **S3 게이트웨이**(무료), DNS 두 개 체크.

## 2. 보안 그룹

| 이름 | 인바운드 | 아웃바운드 |
|---|---|---|
| `gongsi-ec2-sg` | **없음** (접속은 Session Manager) | 기본값 |
| `gongsi-rds-sg` | PostgreSQL 5432, 소스 `gongsi-ec2-sg` | 기본값 |

## 3. IAM — EC2 역할

**역할 생성 → AWS 서비스 → EC2**, 정책 `AmazonSSMManagedInstanceCore`, 이름 `gongsi-ec2-role`.
인라인 정책 `gongsi-ec2-app`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "ssm:GetParametersByPath",
      "Resource": "arn:aws:ssm:ap-southeast-2:437075213716:parameter/gongsi" },
    { "Effect": "Allow", "Action": ["ssm:GetParameter", "ssm:GetParameters"],
      "Resource": "arn:aws:ssm:ap-southeast-2:437075213716:parameter/gongsi/*" },
    { "Effect": "Allow", "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::gongsi-deploy-437075213716" },
    { "Effect": "Allow", "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::gongsi-deploy-437075213716/*" }
  ]
}
```

S3 권한은 8단계 데이터 이전에만 쓴다.

## 4. EC2

**인스턴스 시작**: 이름 `gongsi-app`, Amazon Linux 2023 **64비트(Arm)**, **t4g.micro**,
키 페어 **없이 진행**, VPC `gongsi-vpc`, 서브넷 **public1**, 퍼블릭 IP **활성화**, 보안 그룹 `gongsi-ec2-sg`,
IAM 인스턴스 프로파일 `gongsi-ec2-role`, 사용자 데이터 [user-data.sh](../../deploy/ec2/user-data.sh).

상태 검사 2/2 후 **연결 → Session Manager**:

```bash
java -version 2>&1 | head -1; psql --version; free -m | grep -i swap
# 금감원은 curl 기본 User-Agent 를 끊는다 - Java UA 로 확인 (미등록 인증키 응답이면 통과)
curl -sS -A 'Java/21' 'https://finlife.fss.or.kr/finlifeapi/depositProductsSearch.json?auth=test&topFinGrpNo=020000&pageNo=1'
```

## 5. RDS

1. **서브넷 그룹** `gongsi-db-subnets`: `gongsi-vpc`, 2a·2b 의 **private 서브넷 2개만**
2. **데이터베이스 생성 → 전체 구성**(손쉬운 생성은 Default VPC 로 고정되므로 쓰지 않는다)

| 항목 | 값 |
|---|---|
| 엔진 / 버전 | PostgreSQL **16.x** |
| 템플릿 / 배포 | 프리 티어 / **단일 AZ** |
| 식별자 / 마스터 사용자 | `gongsi-db` / `gongsi` |
| 자격 증명 | **자체 관리** (`/ ' " @` 금지) |
| 클래스 / 스토리지 | **db.t4g.micro** / gp3 20GiB, 자동 조정 끔 |
| VPC / 서브넷 그룹 | `gongsi-vpc` / `gongsi-db-subnets` |
| 퍼블릭 액세스 / 보안 그룹 | **아니요** / `gongsi-rds-sg` 만 |
| 포트 | **5432** |
| 초기 DB 이름 | `gongsi` |
| 백업 보존 | **1일** (무료 플랜 상한, 7일은 거부됨) |
| 향상된 모니터링·로그 내보내기·백업 복제 | 끔 |
| 삭제 방지 | 켬 |

## 6. SSM Parameter Store

표준 계층으로 다섯 개. 이름 끝부분이 환경변수 이름이 된다 ([run.sh](../../deploy/ec2/run.sh)).

| 이름 | 유형 | 값 |
|---|---|---|
| `/gongsi/FINLIFE_API_KEY` | SecureString | 금감원 인증키 |
| `/gongsi/DB_PASS` | SecureString | RDS 마스터 암호 |
| `/gongsi/DB_HOST` | String | RDS 엔드포인트 (호스트 이름만) |
| `/gongsi/DB_USER` | String | `gongsi` |
| `/gongsi/DB_NAME` | String | `gongsi` |

## 7. 첫 배포

1. PR 을 main 에 머지 → **Actions → Release** 가 `build-<sha>` 릴리스를 만든다
2. Session Manager 에서 **한 번만**:

```bash
curl -fsSL https://github.com/junhyeokkk/gongsi/releases/latest/download/deploy.sh | sudo bash
```

3. `배포 완료: <sha>` 가 나오면 성공. 이후 main 머지는 타이머가 10분 안에 반영한다

```bash
systemctl status gongsi --no-pager
systemctl list-timers gongsi-update.timer --no-pager
sudo journalctl -u gongsi -n 50 --no-pager          # Flyway V1 적용, Started GongsiApplication
sudo journalctl -u gongsi-update -n 20 --no-pager   # 배포 기록
```

- 기동 확인에 실패하면 직전 jar 로 자동 복구하고 그 버전을 `/opt/gongsi/FAILED_VERSION` 에 남겨 다시 시도하지 않는다
- 즉시 재배포: `sudo systemctl start gongsi-update`

## 8. 데이터 이전 (다시 받을 수 없는 날짜)

7단계로 RDS 에 스키마가 생긴 **뒤에** 한다. 2026-10-30 기대값: `rows = 15`, `fingerprint = fe6888041ca597774d58bec43ded7193`

**로컬 PC** (레포 루트):

```bash
deploy/migrate/export-raw-snapshot.sh 2026-10-30 > .migration/raw_snapshot_2026-10-30.csv
docker compose exec -T postgres psql -U gongsi -d gongsi -v d=2026-10-30 < deploy/migrate/fingerprint.sql
```

**S3 콘솔**: 버킷 `gongsi-deploy-437075213716`(없으면 기본값으로 생성) → 폴더 `migration` → 업로드
`.migration/raw_snapshot_2026-10-30.csv`, `deploy/migrate/import.sql`, `deploy/migrate/fingerprint.sql`

**EC2 Session Manager**:

```bash
export AWS_REGION=ap-southeast-2 B=gongsi-deploy-437075213716
cd /tmp && aws s3 cp --recursive s3://$B/migration/ migration/ && cd migration

export PGHOST=$(aws ssm get-parameter --name /gongsi/DB_HOST --query Parameter.Value --output text)
export PGPASSWORD=$(aws ssm get-parameter --name /gongsi/DB_PASS --with-decryption --query Parameter.Value --output text)
export PGUSER=gongsi PGDATABASE=gongsi PGSSLMODE=require

psql -v d=2026-10-30 -f fingerprint.sql                              # 이전 전: rows 0
psql -v ON_ERROR_STOP=1 -f import.sql < raw_snapshot_2026-10-30.csv  # COPY 15, COMMIT
psql -v d=2026-10-30 -f fingerprint.sql                              # 로컬과 같아야 함
```

- 지문이 다르면 멈추고 RDS 의 그 날짜를 지운 뒤 다시 한다 — 로컬 원본은 지우지 않는다
- 같은 날짜를 두 번 넣으면 UNIQUE 위반으로 전체 롤백된다
- 끝나면 S3 `migration/` 삭제, `unset PGPASSWORD`

## 9. 매일 실행 확인

```bash
sudo journalctl -u gongsi --since today --no-pager | grep dailyCollectJob
psql -c "SELECT fetched_on, count(*) FROM raw_snapshot GROUP BY 1 ORDER BY 1"
```

15:00 재시도까지 실패하면 서버에서 수동 실행:

```bash
sudo systemctl stop gongsi
sudo -u gongsi AWS_REGION=ap-southeast-2 /opt/gongsi/run.sh \
  --spring.main.web-application-type=none --spring.batch.job.enabled=true \
  --spring.batch.job.name=dailyCollectJob targetDate=YYYY-MM-DD
sudo systemctl start gongsi
```

## 롤백 · 정리

- 롤백: `sudo cp /opt/gongsi/app.jar.prev /opt/gongsi/app.jar && sudo systemctl restart gongsi`
- 중단: RDS 최종 스냅샷 → 삭제 방지 끄고 삭제 → EC2 종료 → S3·파라미터·역할 삭제.
  크레딧이 끝나기 전에 반드시 `pg_dump` 를 로컬로 받아 둔다 (ADR-0006)
