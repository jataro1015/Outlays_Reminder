from __future__ import annotations

import argparse
import json
import smtplib
import sys
from dataclasses import dataclass
from datetime import date, datetime, timezone
from email.message import EmailMessage
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

try:
    import yaml
except ImportError:  # pragma: no cover - optional dependency guard
    yaml = None


DEFAULT_CONFIG_PATH = Path("config.yml")


@dataclass
class SummaryMailSettings:
    input_json: Path
    recipient: str
    sender: str
    subject: str
    dry_run: bool
    smtp_host: str
    smtp_port: int
    smtp_username: Optional[str]
    smtp_password: Optional[str]
    smtp_use_tls: bool
    smtp_use_ssl: bool
    smtp_timeout: int
    profile_name: str
    state_file: Optional[Path]
    target_date: date


@dataclass
class DiffResult:
    diff_count: int
    diff_amount: int
    year_month: str
    state_payload: Dict[str, Any]


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="import_outlays の結果JSONを読み取り、件数と合計金額のサマリーをメール送信する。"
    )
    parser.add_argument("--config", type=Path, default=None, help="設定ファイル (YAML/JSON)。")
    parser.add_argument("--profile", type=str, default=None, help="summary_profiles のプロファイル名。")
    parser.add_argument("--input", type=Path, default=None, help="サマリー対象のJSONファイルを直接指定する。")
    parser.add_argument("--recipient", type=str, default=None, help="メール送信先を上書きする。")
    parser.add_argument("--sender", type=str, default=None, help="メール送信元アドレスを上書きする。")
    parser.add_argument("--subject", type=str, default=None, help="メール件名を上書きする。")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="強制的にドライラン（メール送信せず内容のみ表示）にする。",
    )
    parser.add_argument(
        "--send",
        action="store_true",
        help="設定ファイルでdry_run=trueでも実際に送信する。",
    )
    parser.add_argument(
        "--state-file",
        type=Path,
        default=None,
        help="差分管理用のstateファイルを直接指定します。",
    )
    parser.add_argument(
        "--target-date",
        type=str,
        default=None,
        help="対象日 (YYYY-MM-DD)。指定が無い場合はシステム日付を使用します。",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    config_path = resolve_config_path(args.config)
    profile_data: Dict[str, Any] = {}
    profile_name: Optional[str] = None
    if config_path:
        profile_data, profile_name = load_summary_profile(config_path, args.profile)
    settings = build_settings(args, profile_data, profile_name)

    try:
        records = load_imported_records(settings.input_json)
    except Exception as exc:
        print(f"JSONの読み込みに失敗しました: {exc}", file=sys.stderr)
        return 1

    count, total_amount = summarize_records(records)
    diff_result: Optional[DiffResult] = None
    if settings.state_file:
        try:
            diff_result = compute_monthly_diff(
                settings, count=count, total_amount=total_amount
            )
        except ValueError as exc:
            print(f"stateファイルの読み込みに失敗しました: {exc}", file=sys.stderr)
    text_body, html_body = render_summary_body(
        records,
        count=count,
        total_amount=total_amount,
        source=settings.input_json,
        diff=diff_result,
    )

    if settings.dry_run:
        print("=== Summary Mail (DRY RUN) ===")
        print(f"To: {settings.recipient}")
        print(f"From: {settings.sender}")
        print(f"Subject: {settings.subject}")
        print(text_body)
        return 0

    try:
        send_mail(text_body, html_body, settings)
    except Exception as exc:
        print(f"メール送信に失敗しました: {exc}", file=sys.stderr)
        return 2

    if diff_result and settings.state_file:
        persist_state_file(settings, diff_result)

    print(
        f"サマリーメールを {settings.recipient} へ送信しました。"
        f" (件数={count}, 合計金額={total_amount:,}円)"
    )
    return 0


def build_settings(
    args: argparse.Namespace,
    profile_data: Dict[str, Any],
    profile_name: Optional[str],
) -> SummaryMailSettings:
    input_json = Path(
        args.input
        or profile_data.get("input_json")
        or "outputs/imported_outlays.json"
    )
    recipient = args.recipient or str(profile_data.get("recipient") or "jataro.10.15@gmail.com")
    sender = args.sender or str(profile_data.get("sender") or "outlays-reminder@example.com")
    subject = args.subject or str(
        profile_data.get("subject") or "[Outlays] 取り込みサマリー"
    )

    dry_run = bool(profile_data.get("dry_run", True))
    if args.dry_run:
        dry_run = True
    elif args.send:
        dry_run = False

    smtp_conf = profile_data.get("smtp") or {}
    smtp_host = str(smtp_conf.get("host") or "")
    if not smtp_host:
        raise ValueError("summary_profiles.smtp.host が設定されていません。")
    smtp_port = int(smtp_conf.get("port", 587))
    smtp_username = smtp_conf.get("username")
    smtp_password = smtp_conf.get("password")
    smtp_use_tls = bool(smtp_conf.get("use_tls", True))
    smtp_use_ssl = bool(smtp_conf.get("use_ssl", False))
    smtp_timeout = int(smtp_conf.get("timeout", 30))

    resolved_profile = (
        profile_name or args.profile or profile_data.get("profile") or "default"
    )
    state_file_raw = args.state_file or profile_data.get("state_file")
    state_file = (
        Path(state_file_raw)
        if state_file_raw
        else None
    )
    target_date = determine_target_date(
        args.target_date or profile_data.get("target_date")
    )

    return SummaryMailSettings(
        input_json=input_json,
        recipient=recipient,
        sender=sender,
        subject=subject,
        dry_run=dry_run,
        smtp_host=smtp_host,
        smtp_port=smtp_port,
        smtp_username=smtp_username,
        smtp_password=smtp_password,
        smtp_use_tls=smtp_use_tls,
        smtp_use_ssl=smtp_use_ssl,
        smtp_timeout=smtp_timeout,
        profile_name=str(resolved_profile),
        state_file=state_file,
        target_date=target_date,
    )


def load_imported_records(path: Path) -> List[Dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"ファイルが見つかりません: {path}")
    raw_text = path.read_text(encoding="utf-8")
    if not raw_text.strip():
        return []
    data = json.loads(raw_text)
    if isinstance(data, dict):
        items = data.get("items") or []
    elif isinstance(data, list):
        items = data
    else:
        raise ValueError("JSON形式が想定と異なります。")

    normalized: List[Dict[str, Any]] = []
    for entry in items:
        if not isinstance(entry, dict):
            continue
        amount = parse_amount(entry.get("amount"))
        normalized.append(
            {
                "item": str(entry.get("item") or ""),
                "amount": amount,
            }
        )
    return normalized


def parse_amount(value: Any) -> int:
    if value is None:
        return 0
    try:
        return int(str(value).replace(",", ""))
    except ValueError:
        return 0


def summarize_records(records: List[Dict[str, Any]]) -> Tuple[int, int]:
    total_amount = sum(max(0, rec.get("amount", 0)) for rec in records)
    return len(records), total_amount


def determine_target_date(raw: Optional[str]) -> date:
    if raw:
        try:
            return datetime.strptime(str(raw), "%Y-%m-%d").date()
        except ValueError as exc:
            raise ValueError("--target-date は YYYY-MM-DD 形式で指定してください。") from exc
    return date.today()


def load_state_payload(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {}
    raw_text = path.read_text(encoding="utf-8").strip()
    if not raw_text:
        return {}
    return json.loads(raw_text)


def compute_monthly_diff(
    settings: SummaryMailSettings, *, count: int, total_amount: int
) -> DiffResult:
    if settings.state_file is None:
        raise ValueError("stateファイルが設定されていません。")
    state = load_state_payload(settings.state_file)
    profiles = state.get("profiles") or {}
    current_month = settings.target_date.strftime("%Y-%m")
    profile_state = profiles.get(settings.profile_name) or {}
    if profile_state.get("year_month") == current_month:
        cumulative_count = int(profile_state.get("count", 0))
        cumulative_amount = int(profile_state.get("amount", 0))
    else:
        cumulative_count = 0
        cumulative_amount = 0

    new_total_count = cumulative_count + count
    new_total_amount = cumulative_amount + total_amount

    updated_profiles = dict(profiles)
    updated_profiles[settings.profile_name] = {
        "year_month": current_month,
        "count": new_total_count,
        "amount": new_total_amount,
    }
    updated_state = dict(state)
    updated_state["profiles"] = updated_profiles
    return DiffResult(
        diff_count=new_total_count,
        diff_amount=new_total_amount,
        year_month=current_month,
        state_payload=updated_state,
    )


def persist_state_file(settings: SummaryMailSettings, diff: DiffResult) -> None:
    if settings.state_file is None:
        return
    state_copy = dict(diff.state_payload)
    profiles = dict(state_copy.get("profiles", {}))
    profile_state = dict(profiles.get(settings.profile_name, {}))
    profile_state["last_sent_at"] = datetime.now(timezone.utc).isoformat()
    profiles[settings.profile_name] = profile_state
    state_copy["profiles"] = profiles
    settings.state_file.parent.mkdir(parents=True, exist_ok=True)
    settings.state_file.write_text(
        json.dumps(state_copy, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def format_signed(value: int) -> str:
    return f"+{value}" if value >= 0 else str(value)


def format_diff_html(value: int, unit: str) -> str:
    if value > 0:
        color = "#008000"
    elif value < 0:
        color = "#CC0000"
    else:
        color = None
    content = f"{format_signed(value)}{unit}"
    if color:
        return f'<strong style="color: {color};">{content}</strong>'
    return f"<strong>{content}</strong>"


def render_summary_body(
    records: List[Dict[str, Any]],
    *,
    count: int,
    total_amount: int,
    source: Path,
    diff: Optional[DiffResult] = None,
) -> Tuple[str, str]:
    text_lines = [
        "Outlays Reminder 取り込みサマリー",
        f"対象ファイル: {source}",
    ]
    html_lines = [
        "<html><body>",
        "<p>Outlays Reminder 取り込みサマリー</p>",
        f"<p>対象ファイル: {source}</p>",
    ]
    if diff and diff.year_month:
        text_lines.append(f"対象年月: {diff.year_month}")
        html_lines.append(f"<p>対象年月: {diff.year_month}</p>")

    count_line = f"インポート件数: {count}"
    if diff:
        count_line += f" (月内 {format_signed(diff.diff_count)}件)"
        count_html = (
            f"インポート件数: {count} (月内 {format_diff_html(diff.diff_count, '件')})"
        )
    else:
        count_html = f"インポート件数: {count}"
    text_lines.append(count_line)
    html_lines.append(f"<p>{count_html}</p>")

    amount_line = f"合計金額: {total_amount:,} 円"
    if diff:
        amount_line += f" (月内 {format_signed(diff.diff_amount)}円)"
        amount_html = (
            f"合計金額: {total_amount:,} 円 (月内 {format_diff_html(diff.diff_amount, '円')})"
        )
    else:
        amount_html = f"合計金額: {total_amount:,} 円"
    text_lines.append(amount_line)
    html_lines.append(f"<p>{amount_html}</p>")

    text_lines.append("")
    html_lines.append("<hr>")
    if records:
        text_lines.append("内訳（最大5件）:")
        html_lines.append("<p>内訳（最大5件）:</p><ul>")
        for record in records[:5]:
            item = record["item"] or "不明"
            amount = f"{record['amount']:,} 円"
            text_lines.append(f"- {item} : {amount}")
            html_lines.append(f"<li><strong>{item}</strong> : {amount}</li>")
        html_lines.append("</ul>")
    else:
        text_lines.append("取り込み済みのレコードはありませんでした。")
        html_lines.append("<p>取り込み済みのレコードはありませんでした。</p>")
    text_lines.append("")
    text_lines.append("※このメールは自動送信されています。")
    html_lines.append("<p>※このメールは自動送信されています。</p>")
    html_lines.append("</body></html>")
    return "\n".join(text_lines), "\n".join(html_lines)


def send_mail(text_body: str, html_body: str, settings: SummaryMailSettings) -> None:
    message = EmailMessage()
    message["To"] = settings.recipient
    message["From"] = settings.sender
    message["Subject"] = settings.subject
    message.set_content(text_body)
    if html_body:
        message.add_alternative(html_body, subtype="html")

    if settings.smtp_use_ssl:
        smtp_cls = smtplib.SMTP_SSL
    else:
        smtp_cls = smtplib.SMTP

    with smtp_cls(settings.smtp_host, settings.smtp_port, timeout=settings.smtp_timeout) as client:
        if settings.smtp_use_tls and not settings.smtp_use_ssl:
            client.starttls()
        if settings.smtp_username and settings.smtp_password:
            client.login(settings.smtp_username, settings.smtp_password)
        client.send_message(message)


def resolve_config_path(explicit: Optional[Path]) -> Optional[Path]:
    if explicit:
        return explicit
    return DEFAULT_CONFIG_PATH if DEFAULT_CONFIG_PATH.exists() else None


def load_summary_profile(
    path: Path, profile_name: Optional[str]
) -> Tuple[Dict[str, Any], Optional[str]]:
    data = parse_config_file(path)
    profiles = data.get("summary_profiles")
    if not isinstance(profiles, dict):
        raise ValueError("summary_profiles セクションが見つかりません。")
    return select_profile_from_map(profiles, profile_name, label="summaryプロファイル")


def parse_config_file(path: Path) -> Any:
    if not path.exists():
        raise FileNotFoundError(f"設定ファイルが存在しません: {path}")
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
    if yaml is None:
        raise RuntimeError("PyYAML が必要です。`poetry install` を実行してください。")


def select_profile_from_map(
    profiles: Dict[str, Any], profile_name: Optional[str], *, label: str
) -> Tuple[Dict[str, Any], Optional[str]]:
    target = profile_name
    if target is None:
        if "default" in profiles:
            target = "default"
        elif len(profiles) == 1:
            target = next(iter(profiles.keys()))
    if target is None:
        raise ValueError(f"{label}を --profile で指定してください。")
    raw_profile = profiles.get(target)
    if not isinstance(raw_profile, dict):
        raise ValueError(f"{label} {target} の形式が不正です。")
    return raw_profile, target


if __name__ == "__main__":
    sys.exit(main())
