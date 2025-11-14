from __future__ import annotations

import argparse
import re
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, Tuple

try:
    import yaml
except ImportError:  # pragma: no cover - optional dependency guard
    yaml = None

DATE_TOKEN_PATTERN = re.compile(
    r"\s*\b(?:after|before):\s*[0-9]{4}/[0-9]{2}/[0-9]{2}\b", re.IGNORECASE
)


def parse_args(argv: Any = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "指定したgmailプロファイルのクエリに、実行日時の当月1日〜翌月1日を示す "
            "after/before句を自動で埋め込みます。"
        )
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("config.yml"),
        help="更新対象の設定ファイルパス (default: config.yml)",
    )
    parser.add_argument(
        "--profile",
        type=str,
        default="one_month",
        help="gmail_profiles 内で更新するプロファイル名 (default: one_month)",
    )
    parser.add_argument(
        "--base-query",
        type=str,
        default=None,
        help="after/before を追加する前段のクエリ。未指定時は現在の query から自動抽出。",
    )
    parser.add_argument(
        "--reference-date",
        type=str,
        default=None,
        help="YYYY-MM-DD 形式で月次計算の基準日を指定 (テスト用途)。",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="ファイルを書き換えずに生成結果のみ表示します。",
    )
    return parser.parse_args(argv)


def ensure_yaml_available() -> None:
    if yaml is None:
        raise RuntimeError(
            "PyYAML が見つかりません。`poetry install` 済みの仮想環境で実行してください。"
        )


def load_config(path: Path) -> Dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"設定ファイルが見つかりません: {path}")
    ensure_yaml_available()
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def save_config(path: Path, data: Dict[str, Any]) -> None:
    ensure_yaml_available()
    path.parent.mkdir(parents=True, exist_ok=True)
    yaml_text = yaml.safe_dump(
        data, sort_keys=False, allow_unicode=True, encoding=None, default_flow_style=False
    )
    path.write_text(yaml_text, encoding="utf-8")


def resolve_reference_date(ref: str | None) -> date:
    if ref:
        try:
            parsed = datetime.strptime(ref, "%Y-%m-%d")
            return parsed.date()
        except ValueError as exc:
            raise ValueError("reference-date は YYYY-MM-DD 形式で指定してください。") from exc
    return datetime.now().date()


def month_bounds(target_date: date) -> Tuple[date, date]:
    start = target_date.replace(day=1)
    if start.month == 12:
        next_month = start.replace(year=start.year + 1, month=1)
    else:
        next_month = start.replace(month=start.month + 1)
    return start, next_month


def strip_date_tokens(query: str | None) -> str:
    if not query:
        return ""
    stripped = DATE_TOKEN_PATTERN.sub("", query)
    stripped = re.sub(r"\s{2,}", " ", stripped)
    return stripped.strip()


def build_range_query(base_query: str, start: date, next_month: date) -> str:
    tokens = []
    if base_query:
        tokens.append(base_query.strip())
    tokens.append(f"after:{start.strftime('%Y/%m/%d')}")
    tokens.append(f"before:{next_month.strftime('%Y/%m/%d')}")
    return " ".join(tokens)


def update_profile_query(config: Dict[str, Any], profile_name: str, query: str) -> None:
    profiles = config.get("gmail_profiles")
    if not isinstance(profiles, dict):
        raise ValueError("config.yml に gmail_profiles が見つかりません。")
    profile = profiles.get(profile_name)
    if not isinstance(profile, dict):
        raise ValueError(f"指定されたプロファイルが存在しません: {profile_name}")
    profile["query"] = query


def main(argv: Any = None) -> int:
    args = parse_args(argv)
    try:
        target_date = resolve_reference_date(args.reference_date)
        start, next_month = month_bounds(target_date)
        config = load_config(args.config)

        profiles = config.get("gmail_profiles") or {}
        profile = profiles.get(args.profile)
        if not isinstance(profile, dict):
            raise ValueError(f"指定されたプロファイルが存在しません: {args.profile}")

        base_query = args.base_query or strip_date_tokens(profile.get("query"))
        new_query = build_range_query(base_query, start, next_month)

        if args.dry_run:
            print(new_query)
            return 0

        update_profile_query(config, args.profile, new_query)
        save_config(args.config, config)
        print(
            f"{args.config} の gmail_profiles.{args.profile}.query を更新しました: {new_query}"
        )
    except Exception as exc:  # pragma: no cover - CLI safety net
        print(str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

