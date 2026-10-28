"""
금감원 금융상품한눈에 Open API 사전 조사용 수집 스크립트.

서비스 × 권역 × 전체 페이지를 호출해 응답 원문을 survey/raw/ 에 저장하고,
끝에 서비스·권역별 요약(total_count / 페이지 수 / base·option 행 수)을 출력한다.

- 표준 라이브러리만 사용 (의존성 0)
- 인증키는 .env 의 FINLIFE_API_KEY 에서 읽는다
- 호출 실패는 해당 조합만 errors.log 에 기록하고 계속 진행한다
"""
from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

BASE_URL = "https://finlife.fss.or.kr/finlifeapi"
SERVICES = [
    "depositProductsSearch",       # 정기예금
    "savingProductsSearch",        # 적금
    "mortgageLoanProductsSearch",  # 주택담보대출
    "rentHouseLoanProductsSearch", # 전세자금대출
    "creditLoanProductsSearch",    # 개인신용대출
]
GROUPS = ["020000", "030300"]  # 은행, 저축은행
SLEEP_SEC = 0.3
TIMEOUT_SEC = 15

ROOT = Path(__file__).resolve().parent.parent
RAW_DIR = ROOT / "survey" / "raw"
ERR_LOG = ROOT / "survey" / "errors.log"


def load_api_key() -> str:
    env = ROOT / ".env"
    if not env.exists():
        sys.exit(".env 가 없다. .env.example 을 복사해 FINLIFE_API_KEY 를 채워라.")
    for line in env.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        if k.strip() == "FINLIFE_API_KEY":
            v = v.strip().strip('"').strip("'")
            if v:
                return v
    sys.exit(".env 에 FINLIFE_API_KEY 값이 비어 있다.")


def fetch(service: str, group: str, page: int, key: str) -> dict:
    qs = urllib.parse.urlencode({"auth": key, "topFinGrpNo": group, "pageNo": page})
    url = f"{BASE_URL}/{service}.json?{qs}"
    req = urllib.request.Request(url, headers={"User-Agent": "gongsi-survey/0.1"})
    with urllib.request.urlopen(req, timeout=TIMEOUT_SEC) as res:
        return json.loads(res.read().decode("utf-8"))


def log_error(service: str, group: str, page: int, err: Exception) -> None:
    with ERR_LOG.open("a", encoding="utf-8") as f:
        f.write(f"{date.today().isoformat()}\t{service}\t{group}\tp{page}\t{type(err).__name__}: {err}\n")


def main() -> None:
    key = load_api_key()
    today = date.today().strftime("%Y%m%d")
    RAW_DIR.mkdir(parents=True, exist_ok=True)

    summary: list[tuple[str, str, str, int, int, int]] = []

    for service in SERVICES:
        for group in GROUPS:
            page = 1
            total_count = "?"
            max_page = 1
            base_rows = 0
            option_rows = 0
            while page <= max_page:
                try:
                    body = fetch(service, group, page, key)
                except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
                    print(f"  FAIL {service} {group} p{page}: {e}")
                    log_error(service, group, page, e)
                    page += 1
                    time.sleep(SLEEP_SEC)
                    continue

                result = body.get("result", {})
                err_cd = result.get("err_cd")
                if err_cd != "000":
                    msg = f"err_cd={err_cd} err_msg={result.get('err_msg')}"
                    print(f"  FAIL {service} {group} p{page}: {msg}")
                    log_error(service, group, page, RuntimeError(msg))
                    break

                out = RAW_DIR / f"{service}_{group}_p{page}_{today}.json"
                out.write_text(json.dumps(body, ensure_ascii=False, indent=2), encoding="utf-8")

                total_count = result.get("total_count", "?")
                max_page = int(result.get("max_page_no") or 1)
                base_rows += len(result.get("baseList") or [])
                option_rows += len(result.get("optionList") or [])
                print(f"  ok   {service} {group} p{page}/{max_page}  total={total_count}")

                page += 1
                time.sleep(SLEEP_SEC)

            summary.append((service, group, str(total_count), max_page, base_rows, option_rows))

    print("\n=== 요약 (01 §6 실측값) ===")
    print(f"{'service':<30}{'group':<8}{'total':>7}{'pages':>7}{'base':>7}{'option':>8}")
    for s, g, t, p, b, o in summary:
        print(f"{s:<30}{g:<8}{t:>7}{p:>7}{b:>7}{o:>8}")
    print(f"\n저장 위치: {RAW_DIR}")
    if ERR_LOG.exists():
        print(f"실패 기록: {ERR_LOG}")


if __name__ == "__main__":
    main()
