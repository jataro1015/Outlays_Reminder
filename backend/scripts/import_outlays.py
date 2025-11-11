from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import requests

try:
    import yaml
except ImportError:  # pragma: no cover - optional dependency guard
    yaml = None

DEFAULT_CONFIG_PATH = Path("config.yml")


@dataclass
class IngestSettings:
    input_json: Path
    api_base_url: str
    headers: Dict[str, str]
    item_field: str
    amount_source: str
    amount_pattern: str
    min_amount: int
    skip_without_amount: bool
    dry_run: bool
    limit: Optional[int] = None


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    """CLI引数を定義し、スクリプト実行時の設定を受け取る。"""
    parser = argparse.ArgumentParser(
        description="Gmail取得済みJSONをOutlays APIへ連携します。"
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=None,
        help="設定ファイル (YAML/JSON)。未指定で config.yml があれば自動利用。",
    )
    parser.add_argument(
        "--profile",
        type=str,
        default=None,
        help="ingest_profiles 内のプロファイル名。未指定時はdefaultや単一エントリを選択。",
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=None,
        help="入力JSONを直接指定する場合に使用。",
    )
    parser.add_argument(
        "--api-base-url",
        type=str,
        default=None,
        help="Outlays APIのベースURLを上書き。",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="APIを呼び出さず抽出結果のみ表示する。",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="処理する最大件数。先頭からlimit件のみを対象にする。",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    """エントリポイント。設定を読み取り、メールをAPIへ連携する。"""
    args = parse_args(argv)
    config_path = resolve_config_path(args.config)
    profile_data: Dict[str, Any] = {}
    if config_path:
        profile_data, _ = load_ingest_profile(config_path, args.profile)
    settings = build_settings(args, profile_data)
    try:
        messages = load_messages(settings.input_json)
    except Exception as exc:  # pragma: no cover - simple CLI guard
        print(str(exc), file=sys.stderr)
        return 1

    return ingest_messages(messages, settings)


def resolve_config_path(explicit: Optional[Path]) -> Optional[Path]:
    """--configが無い場合は既定のconfig.ymlを自動で参照する。"""
    if explicit:
        return explicit
    return DEFAULT_CONFIG_PATH if DEFAULT_CONFIG_PATH.exists() else None


def load_ingest_profile(
    path: Path, profile_name: Optional[str]
) -> Tuple[Dict[str, Any], Optional[str]]:
    """config.ymlからingestプロファイルを取得する。"""
    data = parse_config_file(path)
    if not isinstance(data, dict):
        raise ValueError(f"設定ファイルの形式が不正です: {path}")

    profiles = data.get("ingest_profiles")
    if not isinstance(profiles, dict):
        raise ValueError(
            f"ingest_profiles セクションが設定ファイルに見つかりません: {path}"
        )
    return select_profile_from_map(
        profiles, profile_name, label="ingestプロファイル"
    )


def parse_config_file(path: Path) -> Any:
    """YAML/JSONファイルを読み込みPythonオブジェクトへ変換する。"""
    if not path.exists():
        raise FileNotFoundError(f"設定ファイルが見つかりません: {path}")
    content = path.read_text(encoding="utf-8")
    suffix = path.suffix.lower()
    if suffix in {".yml", ".yaml"}:
        ensure_yaml_available()
        return yaml.safe_load(content) or {}
    if suffix == ".json":
        return json.loads(content or "{}")

    try:
        ensure_yaml_available()
        return yaml.safe_load(content) or {}
    except Exception:
        return json.loads(content or "{}")


def ensure_yaml_available() -> None:
    """PyYAML未導入時にエラーを出して明示的に案内する。"""
    if yaml is None:
        raise RuntimeError(
            "YAML設定を読み込むには PyYAML が必要です。`poetry install` を実行してください。"
        )


def select_profile_from_map(
    profiles: Dict[str, Any], profile_name: Optional[str], *, label: str
) -> Tuple[Dict[str, Any], Optional[str]]:
    """プロファイル辞書から対象エントリを選択して返す。"""
    target = profile_name
    if target is None:
        if "default" in profiles:
            target = "default"
        elif len(profiles) == 1:
            target = next(iter(profiles.keys()))
    if target is None:
        available = ", ".join(profiles.keys())
        raise ValueError(
            f"{label}が複数定義されています。--profile で対象を指定してください。候補: {available}"
        )
    raw_profile = profiles.get(target)
    if raw_profile is None:
        raise ValueError(f"指定したプロファイルが見つかりません: {target}")
    if not isinstance(raw_profile, dict):
        raise ValueError(f"プロファイル {target} の構造が不正です。")
    return raw_profile, target


def build_settings(args: argparse.Namespace, profile_data: Dict[str, Any]) -> IngestSettings:
    """CLI引数とプロファイル設定を束ねてIngestSettingsへ整形する。"""
    input_json = Path(
        args.input
        or profile_data.get("input_json")
        or profile_data.get("input")
        or "outputs/out.json"
    )
    api_base_url = args.api_base_url or profile_data.get("api_base_url") or profile_data.get(
        "api"
    )
    if not api_base_url:
        # 設定側で未指定の場合でもローカルAPIに向けて動かせるよう既定URLを適用
        api_base_url = "http://localhost:8080/api/v1/outlays"

    headers = profile_data.get("headers") or {}
    headers = {str(k): str(v) for k, v in headers.items()}  # headersがあっても型を正規化
    if "Content-Type" not in headers:
        # API連携でJSONを送る想定なので明示的にContent-Typeを補完
        headers["Content-Type"] = "application/json"

    # 設定がなくても基本的な抽出が行えるようにデフォルト値をセット
    item_field = str(profile_data.get("item_field", "subject"))
    amount_source = str(profile_data.get("amount_source", "body"))
    amount_pattern = str(
        profile_data.get("amount_pattern", r"([0-9,]+)円")
    ).strip()
    min_amount = int(profile_data.get("min_amount", 1))
    skip_without_amount = bool(profile_data.get("skip_without_amount", True))
    dry_run = args.dry_run or bool(profile_data.get("dry_run", False))

    return IngestSettings(
        input_json=input_json,
        api_base_url=api_base_url,
        headers=headers,
        item_field=item_field,
        amount_source=amount_source,
        amount_pattern=amount_pattern,
        min_amount=min_amount,
        skip_without_amount=skip_without_amount,
        dry_run=dry_run,
        limit=args.limit,
    )


def load_messages(path: Path) -> List[Dict[str, Any]]:
    """gmail_fetcherが出力したJSON配列を読み込む。"""
    if not path.exists():
        raise FileNotFoundError(f"入力JSONが見つかりません: {path}")
    text = path.read_text(encoding="utf-8")
    data = json.loads(text or "[]")
    if not isinstance(data, list):
        raise ValueError("入力JSONの形式が配列ではありません。")
    return data


def ingest_messages(messages: List[Dict[str, Any]], settings: IngestSettings) -> int:
    """各メールを解析し、APIへ登録するメインループ。"""
    compiled_pattern = re.compile(settings.amount_pattern)
    total = success = skipped = failed = 0

    for message in messages:
        if settings.limit is not None and total >= settings.limit:
            break
        total += 1
        item = extract_item(message, settings.item_field)
        amount = extract_amount(message, settings.amount_source, compiled_pattern)

        if amount is None:
            if settings.skip_without_amount:
                skipped += 1
                continue
            amount = 0

        if amount < settings.min_amount:
            skipped += 1
            continue

        payload = {"item": item, "amount": amount}
        if settings.dry_run:
            # ドライラン時はAPIを呼ばずに抽出結果だけを表示する。
            print(f"[DRY RUN] {payload}")
            success += 1
            continue

        try:
            response = requests.post(
                settings.api_base_url,
                json=payload,
                headers=settings.headers,
                timeout=15,
            )
        except requests.RequestException as exc:  # pragma: no cover - network guard
            failed += 1
            print(f"API呼び出しに失敗しました: {exc}", file=sys.stderr)
            continue

        if 200 <= response.status_code < 300:
            success += 1
        else:
            failed += 1
            detail = safe_extract_error(response)
            print(
                f"API呼び出しが失敗しました (status={response.status_code}): {detail}",
                file=sys.stderr,
            )

    print(
        f"処理完了 total={total}, success={success}, skipped={skipped}, failed={failed}."
        + (" (DRY RUN)" if settings.dry_run else "")
    )
    return 0 if failed == 0 else 2


def extract_item(message: Dict[str, Any], field: str) -> str:
    """itemに相当する文字列を件名・本文等から抽出する。"""
    field = field.lower()
    if field == "body":
        body = message.get("body") or ""
        return str(body).strip()[:50] or "メール本文"
    if field == "snippet":
        snippet = message.get("snippet") or ""
        return str(snippet).strip() or "メール概要"

    headers = message.get("headers") or {}
    subject_values = headers.get("Subject") or []
    if subject_values:
        return str(subject_values[0]).strip()
    return str(message.get("snippet") or "メール通知")


def extract_amount(
    message: Dict[str, Any], source: str, pattern: re.Pattern[str]
) -> Optional[int]:
    """本文などから金額を正規表現で抽出し、intへ変換する。"""
    source = source.lower()
    if source == "subject":
        headers = message.get("headers") or {}
        text = " ".join(headers.get("Subject") or [])
    elif source == "snippet":
        text = str(message.get("snippet") or "")
    else:
        text = str(message.get("body") or "")

    match = pattern.search(text)
    if not match:
        return None

    value = match.group(1).replace(",", "")
    try:
        return int(value)
    except ValueError:
        return None


def safe_extract_error(response: requests.Response) -> str:
    """APIエラー時にレスポンス内容を安全に文字列化する。"""
    try:
        data = response.json()
        if isinstance(data, dict) and data.get("message"):
            return str(data["message"])
        return json.dumps(data, ensure_ascii=False)
    except ValueError:
        return response.text


if __name__ == "__main__":
    sys.exit(main())
