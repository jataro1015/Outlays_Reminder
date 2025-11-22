from __future__ import annotations

import argparse
import shlex
import subprocess
import sys
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any, Dict, Optional

import gmail_fetcher


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="gmail_fetcher を日次運用するための薄いラッパ。"
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=None,
        help="gmail_fetcher 用の設定ファイル (YAML/JSON)。省略時は config.yml を探索。",
    )
    parser.add_argument(
        "--profile",
        type=str,
        default=None,
        help="gmail_profiles から利用するプロファイル名。省略時は default。",
    )
    parser.add_argument(
        "--target-date",
        type=str,
        default=None,
        help="取得したい日付 (YYYY-MM-DD)。省略時は前日を対象にします。",
    )
    parser.add_argument(
        "--range-days",
        type=int,
        default=1,
        help="target-date からの取得日数 (デフォルト1日)。",
    )
    parser.add_argument(
        "--output-template",
        type=str,
        default=None,
        help="日次JSONの出力テンプレート。{profile} / {date} / {start} / {end} が利用可。",
    )
    parser.add_argument(
        "--base-query",
        type=str,
        default=None,
        help="config.yml の query を上書きしたい場合に指定。",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="gmail_fetcher を実行せず、計算結果とコマンドのみ表示。",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    config_path = gmail_fetcher.resolve_config_path(args.config)
    if not config_path or not config_path.exists():
        print("config.yml が見つかりません。--config で明示してください。", file=sys.stderr)
        return 1

    profile_data, profile_name = gmail_fetcher.load_config_profile(
        config_path, args.profile
    )
    profile_name = profile_name or "default"

    target_start = determine_target_date(args.target_date)
    target_end = target_start + timedelta(days=max(1, args.range_days))

    base_query = (
        args.base_query
        if args.base_query is not None
        else str(profile_data.get("query") or "").strip()
    )
    query = build_query(base_query, target_start, target_end)

    output_path = determine_output_path(
        args.output_template,
        profile_data,
        profile_name,
        target_start,
        target_end,
    )

    command = build_command(
        config_path=config_path,
        profile_name=profile_name,
        query=query,
        output_path=output_path,
    )

    print(
        f"[daily_gmail_fetcher] profile={profile_name}, "
        f"range={target_start}～{target_end}, output={output_path}"
    )
    if args.dry_run:
        print(" ".join(shlex.quote(str(arg)) for arg in command))
        return 0

    result = subprocess.run(command, check=False)
    return result.returncode


def determine_target_date(value: Optional[str]) -> date:
    if value:
        try:
            return datetime.strptime(value, "%Y-%m-%d").date()
        except ValueError as exc:
            raise SystemExit(f"--target-date の形式が不正です: {value}") from exc
    return date.today() - timedelta(days=1)


def build_query(base_query: str, start: date, end: date) -> str:
    after = start.strftime("%Y/%m/%d")
    before = end.strftime("%Y/%m/%d")
    range_clause = f"after:{after} before:{before}"
    if base_query:
        return f"{base_query} {range_clause}".strip()
    return range_clause


def determine_output_path(
    template: Optional[str],
    profile_data: Dict[str, Any],
    profile_name: str,
    start: date,
    end: date,
) -> Path:
    # 設定ファイルにテンプレートが書かれていれば優先
    pattern = template or profile_data.get("daily_output_template")
    context = {
        "profile": profile_name,
        "date": start.strftime("%Y%m%d"),
        "start": start.strftime("%Y%m%d"),
        "end": end.strftime("%Y%m%d"),
    }
    if pattern:
        return Path(str(pattern).format(**context))

    # 未指定なら outputs/gmail_{profile}_{YYYYMMDD}.json に出力
    return Path("outputs") / f"gmail_{profile_name}_{context['date']}.json"


def build_command(
    *,
    config_path: Path,
    profile_name: str,
    query: str,
    output_path: Path,
) -> list[str]:
    command = [
        sys.executable,
        "-m",
        "gmail_fetcher",
        "--config",
        str(config_path),
        "--profile",
        profile_name,
    ]
    if query:
        command.extend(["--query", query])
    command.extend(["--output", str(output_path)])
    return command


if __name__ == "__main__":
    sys.exit(main())
