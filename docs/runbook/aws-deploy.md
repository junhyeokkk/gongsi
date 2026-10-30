# AWS 배포 런북 — EC2 + RDS

수집 서버를 EC2 1대에 상시 띄우고, 매일 KST 09:00(실패 시 15:00 재시도)에 `dailyCollectJob` 을 돌린다.
결정 배경은 [ADR-0006](../adr/0006-ec2-rds-hosting.md).

```
GitHub Actions ──(OIDC)──▶ S3 releases/<sha> ──▶ SSM Run Command ──▶ EC2 (퍼블릭 서브넷, 인바운드 0)
                                                                      │ 5432
                                                                      ▼
                                                    RDS PostgreSQL 16 (프라이빗 서브넷)
비밀값: SSM Parameter Store /gongsi/*  → EC2 기동 시 run.sh 가 읽음
```

- 리전: **아시아 태평양(서울) ap-northeast-2** — 모든 단계에서 오른쪽 위 리전을 먼저 확인
- 콘솔에서 `<ACCOUNT_ID>` 는 오른쪽 위 계정 메뉴의 12자리 숫자
- '고급 기능 활성화'·'플랜 업그레이드' 버튼은 누르지 않는다
- 각 단계 끝의 **기록** 값은 다음 단계에서 쓴다

> **2026-10-31 데이터 보호**: 그날 15:00 KST 까지 서버 첫 수집이 확인되지 않으면 로컬에서 수동 실행한다.
> ```bash
> set -a && . ./.env && set +a && ./gradlew bootRun --args='--spring.main.web-application-type=none --spring.batch.job.enabled=true --spring.batch.job.name=dailyCollectJob targetDate=2026-10-31'
> ```
> 로컬에서 받았다면 9단계에서 그 날짜도 같이 옮긴다.

---

## 0. 예산 알림 (선택, 무료)

1. **Billing and Cost Management → 예산(Budgets) → 예산 생성**
2. 템플릿 **월별 비용 예산**, 이름 `gongsi-monthly`, 금액 `35` USD, 이메일 입력 → 생성
3. **크레딧 → 남은 크레딧**도 이 화면에서 매주 본다 (ADR-0006 관찰 계획)

## 1. VPC

1. **VPC → VPC 생성 → VPC 등(VPC and more)**
2. 이름 태그 자동 생성: `gongsi`
3. IPv4 CIDR `10.0.0.0/16`, IPv6 없음
4. 가용 영역 **2**, 퍼블릭 서브넷 **2**, 프라이빗 서브넷 **2**
5. NAT 게이트웨이 **없음** (시간당 과금이라 쓰지 않는다), VPC 엔드포인트 **없음**
6. DNS 호스트 이름·DNS 확인 **둘 다 켬** → **VPC 생성**

기록: VPC `gongsi-vpc`

## 2. 보안 그룹

**VPC → 보안 그룹 → 보안 그룹 생성** 을 두 번.

| 이름 | VPC | 인바운드 | 아웃바운드 |
|---|---|---|---|
| `gongsi-ec2-sg` | gongsi-vpc | **규칙 없음** (접속은 Session Manager) | 기본값(전체) |
| `gongsi-rds-sg` | gongsi-vpc | PostgreSQL TCP 5432, 소스 = **사용자 지정 → `gongsi-ec2-sg`** | 기본값 |

## 3. RDS

### 3-1. 서브넷 그룹
1. **RDS → 서브넷 그룹 → DB 서브넷 그룹 생성**
2. 이름 `gongsi-db-subnets`, VPC `gongsi-vpc`
3. 가용 영역 2개 모두 선택, 서브넷은 **이름에 `private` 이 들어간 2개만** 선택 → 생성

### 3-2. DB 생성
**RDS → 데이터베이스 생성**

| 항목 | 값 |
|---|---|
| 생성 방식 | 표준 생성 |
| 엔진 | PostgreSQL, 버전 **16.x** 최신 |
| 템플릿 | 프리 티어 (없으면 개발/테스트) |
| 배포 옵션 | **단일 AZ DB 인스턴스** |
| DB 인스턴스 식별자 | `gongsi-db` |
| 마스터 사용자 이름 | `gongsi` |
| 자격 증명 관리 | **자체 관리** (Secrets Manager 는 월 과금) → 강력한 암호 직접 입력, 따로 보관 |
| 인스턴스 클래스 | 버스터블 → **db.t4g.micro** |
| 스토리지 | gp3 **20 GiB**, 스토리지 자동 조정 **끔** |
| 컴퓨팅 리소스 | EC2 컴퓨팅 리소스에 연결 안 함 |
| VPC / 서브넷 그룹 | `gongsi-vpc` / `gongsi-db-subnets` |
| 퍼블릭 액세스 | **아니요** |
| 보안 그룹 | 기존 선택 → `gongsi-rds-sg` 만 (default 제거) |
| 모니터링 | Performance Insights·확장 모니터링 **끔** |
| 추가 구성 → 초기 DB 이름 | `gongsi` |
| 백업 보존 | 7일 |
| 삭제 방지 | **켬** |

생성 후 몇 분 뒤 **연결 & 보안 → 엔드포인트** 를 복사.

기록: RDS 엔드포인트 `gongsi-db.xxxx.ap-northeast-2.rds.amazonaws.com`

## 4. S3 배포 버킷

1. **S3 → 버킷 만들기**, 이름 `gongsi-deploy-<ACCOUNT_ID>`
2. 퍼블릭 액세스 차단 **모두 켬**(기본값) → 만들기

기록: 버킷 이름

## 5. SSM Parameter Store

**Systems Manager → 파라미터 스토어 → 파라미터 생성**, 계층 **표준**(무료). 다섯 개:

| 이름 | 유형 | 값 |
|---|---|---|
| `/gongsi/FINLIFE_API_KEY` | **SecureString** (KMS 키: `alias/aws/ssm` 기본) | 로컬 `.env` 의 키 |
| `/gongsi/DB_PASS` | **SecureString** | 3-2 에서 정한 암호 |
| `/gongsi/DB_HOST` | String | RDS 엔드포인트 |
| `/gongsi/DB_USER` | String | `gongsi` |
| `/gongsi/DB_NAME` | String | `gongsi` |

이름의 마지막 부분이 그대로 환경변수 이름이 된다 ([run.sh](../../deploy/ec2/run.sh)).

## 6. IAM

### 6-1. EC2 역할
1. **IAM → 역할 → 역할 생성**, 신뢰할 엔터티 **AWS 서비스 → EC2**
2. 권한 정책 `AmazonSSMManagedInstanceCore` 체크 → 이름 `gongsi-ec2-role` → 생성
3. 만든 역할 → **권한 추가 → 인라인 정책 생성 → JSON**, 이름 `gongsi-ec2-app`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "ssm:GetParametersByPath",
      "Resource": "arn:aws:ssm:ap-northeast-2:<ACCOUNT_ID>:parameter/gongsi" },
    { "Effect": "Allow", "Action": ["ssm:GetParameter", "ssm:GetParameters"],
      "Resource": "arn:aws:ssm:ap-northeast-2:<ACCOUNT_ID>:parameter/gongsi/*" },
    { "Effect": "Allow", "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::gongsi-deploy-<ACCOUNT_ID>" },
    { "Effect": "Allow", "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::gongsi-deploy-<ACCOUNT_ID>/*" }
  ]
}
```

### 6-2. GitHub Actions OIDC 공급자
1. **IAM → 자격 증명 공급자 → 공급자 추가**
2. 유형 **OpenID Connect**, URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com` → 추가

### 6-3. 배포 역할
1. **IAM → 역할 → 역할 생성**, 신뢰할 엔터티 **웹 자격 증명**
2. 공급자 `token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`
3. GitHub 조직 `junhyeokkk`, 리포지토리 `gongsi`, 브랜치 `main`
4. 권한 정책은 건너뛰고 이름 `gongsi-github-deploy` → 생성
5. 인라인 정책 `gongsi-deploy` (`<INSTANCE_ID>` 는 7단계 후 채운다 — 그때 이 정책을 편집):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::gongsi-deploy-<ACCOUNT_ID>/releases/*" },
    { "Effect": "Allow", "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:<ACCOUNT_ID>:instance/<INSTANCE_ID>",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ] },
    { "Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*" }
  ]
}
```

기록: 배포 역할 ARN `arn:aws:iam::<ACCOUNT_ID>:role/gongsi-github-deploy`

## 7. EC2

**EC2 → 인스턴스 시작**

| 항목 | 값 |
|---|---|
| 이름 | `gongsi-app` |
| AMI | Amazon Linux 2023, 아키텍처 **64비트(Arm)** |
| 인스턴스 유형 | **t4g.micro** |
| 키 페어 | **키 페어 없이 계속 진행** (접속은 Session Manager) |
| 네트워크 설정 → 편집 | VPC `gongsi-vpc`, 서브넷 **`public` 이 들어간 것**, 퍼블릭 IP 자동 할당 **활성화** |
| 방화벽 | 기존 보안 그룹 → `gongsi-ec2-sg` |
| 스토리지 | 8 GiB gp3 (기본값) |
| 고급 세부 정보 → IAM 인스턴스 프로파일 | `gongsi-ec2-role` |
| 고급 세부 정보 → 사용자 데이터 | [deploy/ec2/user-data.sh](../../deploy/ec2/user-data.sh) 내용 붙여넣기 |

시작 후 3~5분 기다린 뒤 인스턴스 선택 → **연결 → Session Manager → 연결**. 셸에서:

```bash
java -version && psql --version && free -m
```

기록: 인스턴스 ID `i-...` → 6-3 정책의 `<INSTANCE_ID>` 채우기

## 8. GitHub 설정과 첫 배포

1. 레포 **Settings → Secrets and variables → Actions**
   - Secrets: `AWS_DEPLOY_ROLE_ARN` = 6-3 역할 ARN
   - Variables: `EC2_INSTANCE_ID` = 인스턴스 ID, `DEPLOY_BUCKET` = 버킷 이름
2. 배포 PR 을 main 에 머지하면 **Actions → Deploy** 가 돈다. 이후 재배포는 같은 화면 **Run workflow**
3. 로그 끝에 `배포 완료` 와 `status=Success` 가 나오면 성공. Session Manager 에서 확인:

```bash
systemctl status gongsi --no-pager
sudo journalctl -u gongsi -n 50 --no-pager   # Flyway V1 적용, Started GongsiApplication
```

Variables 가 비어 있으면 Deploy 워크플로는 건너뛴다(실패 아님).

## 9. 2026-10-30 데이터 이전 (다시 받을 수 없음)

8단계 첫 배포로 RDS 에 스키마가 생긴 **뒤에** 한다.

**로컬 PC** (레포 루트, 덤프는 이미 `.migration/` 에 있다. 없으면 다시 뽑는다):

```bash
deploy/migrate/export-raw-snapshot.sh 2026-10-30 > .migration/raw_snapshot_2026-10-30.csv
docker compose exec -T postgres psql -U gongsi -d gongsi -v d=2026-10-30 < deploy/migrate/fingerprint.sql
```

기대값: `rows = 15`, `fingerprint = fe6888041ca597774d58bec43ded7193` (2026-10-30 로컬 원본, 왕복 검증 완료)

**S3 콘솔**: 버킷 → **폴더 만들기** `migration` → 업로드 3개
`.migration/raw_snapshot_2026-10-30.csv`, `deploy/migrate/import.sql`, `deploy/migrate/fingerprint.sql`

**EC2 Session Manager**:

```bash
export AWS_REGION=ap-northeast-2 B=gongsi-deploy-<ACCOUNT_ID>
cd /tmp && aws s3 cp --recursive s3://$B/migration/ migration/ && cd migration

export PGHOST=$(aws ssm get-parameter --name /gongsi/DB_HOST --query Parameter.Value --output text)
export PGPASSWORD=$(aws ssm get-parameter --name /gongsi/DB_PASS --with-decryption --query Parameter.Value --output text)
export PGUSER=gongsi PGDATABASE=gongsi PGSSLMODE=require

psql -v d=2026-10-30 -f fingerprint.sql                              # 이전 전: rows 0
psql -v ON_ERROR_STOP=1 -f import.sql < raw_snapshot_2026-10-30.csv  # COPY 15, COMMIT
psql -v d=2026-10-30 -f fingerprint.sql                              # 이전 후: 로컬과 같아야 함
```

- 지문이 **로컬 값과 다르면** 멈추고 RDS 의 그 날짜를 지운 뒤 다시 한다 — 로컬 원본은 지우지 않는다
- 같은 날짜를 두 번 넣으면 UNIQUE 위반으로 **전체 롤백**된다(부분 적재 없음)
- 끝나면 S3 의 `migration/` 폴더 삭제, `unset PGPASSWORD`

## 10. 매일 실행 확인

다음 날 09:00 KST 이후:

```bash
sudo journalctl -u gongsi --since today --no-pager | grep dailyCollectJob
psql -c "SELECT fetched_on, count(*) FROM raw_snapshot GROUP BY 1 ORDER BY 1"
```

- `dailyCollectJob 완료 targetDate=...` 가 보이면 정상
- 09:00 실패분은 15:00 에 자동 재시작. 그래도 실패하면 서버에서 수동 실행:

```bash
sudo systemctl stop gongsi   # 스케줄과 겹치지 않게
sudo -u gongsi AWS_REGION=ap-northeast-2 /opt/gongsi/run.sh \
  --spring.main.web-application-type=none --spring.batch.job.enabled=true \
  --spring.batch.job.name=dailyCollectJob targetDate=YYYY-MM-DD
sudo systemctl start gongsi
```

## 롤백

```bash
sudo cp /opt/gongsi/app.jar.prev /opt/gongsi/app.jar && sudo systemctl restart gongsi
```

## 정리(중단할 때)

RDS **최종 스냅샷 생성** 후 삭제(삭제 방지 먼저 끔) → EC2 종료 → S3 비우고 삭제 → 파라미터·역할 삭제.
크레딧이 끝나기 전에 반드시 `pg_dump` 를 로컬로 받아 둔다 (ADR-0006).
