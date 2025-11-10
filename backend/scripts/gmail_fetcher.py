from __future__ import annotations

import argparse
import base64
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence

from dateutil import parser as date_parser
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from google_auth_oauthlib.flow import InstalledAppFlow

try:
    import keyring
except ImportError:  # pragma: no cover - optional dependency guard
    keyring = None

SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]
ENV_TOKEN_KEYRING_SERVICE = "OUTLAYS_GMAIL_KEYRING_SERVICE"
ENV_TOKEN_KEYRING_USERNAME = "OUTLAYS_GMAIL_KEYRING_USERNAME"
ENV_CREDENTIALS_KEYRING_SERVICE = "OUTLAYS_GMAIL_CREDENTIALS_KEYRING_SERVICE"
ENV_CREDENTIALS_KEYRING_USERNAME = "OUTLAYS_GMAIL_CREDENTIALS_KEYRING_USERNAME"

DEFAULT_TOKEN_KEYRING_SERVICE = os.environ.get(
    ENV_TOKEN_KEYRING_SERVICE, "outlays-reminder-gmail-token"
)
DEFAULT_TOKEN_KEYRING_USERNAME = os.environ.get(
    ENV_TOKEN_KEYRING_USERNAME, "gmail-token"
)
DEFAULT_CREDENTIALS_KEYRING_SERVICE = os.environ.get(
    ENV_CREDENTIALS_KEYRING_SERVICE, "outlays-reminder-gmail-client"
)
DEFAULT_CREDENTIALS_KEYRING_USERNAME = os.environ.get(
    ENV_CREDENTIALS_KEYRING_USERNAME, "gmail-client"
)


@dataclass
class GmailMessage:
    """構造化済みのGmailメッセージ."""

    id: str
    thread_id: str
    label_ids: Sequence[str]
    snippet: str
    internal_date: str
    headers: Dict[str, List[str]]
    body: str

    def to_dict(self) -> Dict[str, object]:
        return {
            "id": self.id,
            "threadId": self.thread_id,
            "labelIds": list(self.label_ids),
            "snippet": self.snippet,
            "internalDate": self.internal_date,
            "headers": {key: list(values) for key, values in self.headers.items()},
            "body": self.body,
        }


def parse_args() -> argparse.Namespace:
    """CLI引数を定義し、ユーザー入力をパースした結果を返す。"""
    parser = argparse.ArgumentParser(
        description="Gmailからメールを取得してJSON形式で出力します。"
    )
    parser.add_argument(
        "--credentials",
        type=Path,
        default=Path("credentials.json"),
        help="OAuth2クライアントシークレットのJSONパス",
    )
    parser.add_argument(
        "--token",
        type=Path,
        default=Path("token.json"),
        help="Keyring未使用時にリフレッシュトークンを保存するJSONパス",
    )
    parser.add_argument(
        "--query",
        type=str,
        default=None,
        help="Gmailの検索クエリ (例: subject:ご利用金額確定)",
    )
    parser.add_argument(
        "--labels",
        nargs="+",
        default=None,
        help="フィルタ対象ラベル (複数指定可)",
    )
    parser.add_argument(
        "--max-results",
        type=int,
        default=20,
        help="取得する最大件数 (1-500までを推奨)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="出力先ファイル。未指定なら標準出力。",
    )
    parser.add_argument(
        "--use-keyring",
        action="store_true",
        help="token.jsonの代わりにKeyringへ資格情報を保存する",
    )
    parser.add_argument(
        "--keyring-service",
        type=str,
        default=DEFAULT_TOKEN_KEYRING_SERVICE,
        help=f"トークンをKeyring保存する際のサービス名 (環境変数 {ENV_TOKEN_KEYRING_SERVICE} でも指定可)",
    )
    parser.add_argument(
        "--keyring-username",
        type=str,
        default=DEFAULT_TOKEN_KEYRING_USERNAME,
        help=f"トークンをKeyring保存する際のユーザー名 (環境変数 {ENV_TOKEN_KEYRING_USERNAME} でも指定可)",
    )
    parser.add_argument(
        "--use-credentials-keyring",
        action="store_true",
        help="credentials.jsonの代わりにKeyringからOAuthクライアント情報を読み込む",
    )
    parser.add_argument(
        "--credentials-keyring-service",
        type=str,
        default=DEFAULT_CREDENTIALS_KEYRING_SERVICE,
        help=(
            "OAuthクライアント情報をKeyringから読み込む際のサービス名 "
            f"(環境変数 {ENV_CREDENTIALS_KEYRING_SERVICE} でも指定可)"
        ),
    )
    parser.add_argument(
        "--credentials-keyring-username",
        type=str,
        default=DEFAULT_CREDENTIALS_KEYRING_USERNAME,
        help=(
            "OAuthクライアント情報をKeyringから読み込む際のユーザー名 "
            f"(環境変数 {ENV_CREDENTIALS_KEYRING_USERNAME} でも指定可)"
        ),
    )
    return parser.parse_args()


def load_credentials(
    credentials_path: Path,
    token_path: Path,
    *,
    use_keyring: bool,
    keyring_service: str,
    keyring_username: str,
    use_credentials_keyring: bool,
    credentials_keyring_service: str,
    credentials_keyring_username: str,
) -> Credentials:
    """credential/tokenストレージから有効なCredentialsを返す。"""
    if not use_credentials_keyring and not credentials_path.exists():
        raise FileNotFoundError(f"credentialsファイルが見つかりません: {credentials_path}")

    creds = load_stored_credentials(
        token_path,
        use_keyring=use_keyring,
        keyring_service=keyring_service,
        keyring_username=keyring_username,
    )

    # Credentialsが存在し、
    # かつcreds.expiredとcreds.refresh_tokenが存在する場合
    # refresh関数を実行する
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = build_oauth_flow(
                credentials_path=credentials_path,
                use_credentials_keyring=use_credentials_keyring,
                credentials_keyring_service=credentials_keyring_service,
                credentials_keyring_username=credentials_keyring_username,
            )
            creds = flow.run_local_server(port=0)
        persist_credentials(
            creds,
            token_path,
            use_keyring=use_keyring,
            keyring_service=keyring_service,
            keyring_username=keyring_username,
        )

    return creds


def load_stored_credentials(
    token_path: Path,
    *,
    use_keyring: bool,
    keyring_service: str,
    keyring_username: str,
) -> Optional[Credentials]:
    """Keyringまたはtoken.jsonからCredentialsを復元する。"""
    if use_keyring:
        ensure_keyring_available()
        secret = keyring.get_password(keyring_service, keyring_username)
        if not secret:
            return None
        try:
            data = json.loads(secret)
        except json.JSONDecodeError:
            return None
        return Credentials.from_authorized_user_info(data, SCOPES)

    if token_path.exists():
        return Credentials.from_authorized_user_file(str(token_path), SCOPES)

    return None


def build_oauth_flow(
    *,
    credentials_path: Path,
    use_credentials_keyring: bool,
    credentials_keyring_service: str,
    credentials_keyring_username: str,
) -> InstalledAppFlow:
    """OAuthクライアント情報の取得先に応じてFlowを生成する。"""
    if use_credentials_keyring:
        config = load_client_config_from_keyring(
            credentials_keyring_service, credentials_keyring_username
        )
        return InstalledAppFlow.from_client_config(config, SCOPES)

    return InstalledAppFlow.from_client_secrets_file(str(credentials_path), SCOPES)


def load_client_config_from_keyring(service: str, username: str) -> Dict[str, object]:
    """Keyringに保持したclient_secret JSONを辞書化して返す。"""
    ensure_keyring_available()
    secret = keyring.get_password(service, username)
    if not secret:
        raise FileNotFoundError(
            f"KeyringにOAuthクライアント情報が見つかりません: service={service}, username={username}"
        )
    try:
        config = json.loads(secret)
    except json.JSONDecodeError as exc:
        raise ValueError("KeyringのOAuthクライアント情報がJSONとして不正です。") from exc
    return config


def persist_credentials(
    creds: Credentials,
    token_path: Path,
    *,
    use_keyring: bool,
    keyring_service: str,
    keyring_username: str,
) -> None:
    """最新のCredentialsをKeyringまたはtoken.jsonへ保存する。"""
    serialized = creds.to_json()
    if use_keyring:
        ensure_keyring_available()
        keyring.set_password(keyring_service, keyring_username, serialized)
        return

    token_path.parent.mkdir(parents=True, exist_ok=True)
    token_path.write_text(serialized, encoding="utf-8")


def ensure_keyring_available() -> None:
    """Keyring未導入時にユーザーへ明示的にエラーを伝える。"""
    if keyring is None:
        raise RuntimeError(
            "Keyringサポートが有効になっていません。`poetry install` を実行してから"
            " Windowsでは資格情報マネージャー等を利用してください。"
        )


def list_message_refs(
    service,
    *,
    query: Optional[str],
    labels: Optional[Sequence[str]],
    max_results: int,
) -> List[Dict[str, str]]:
    """Gmail APIでメッセージID一覧をページングしながら最大max_results件取得する。"""
    max_results = max(1, max_results)
    refs: List[Dict[str, str]] = []
    page_token: Optional[str] = None

    while True:
        request = (
            service.users()
            .messages()
            .list(
                userId="me", # OAuth認証済みユーザー自身を指定。メールアドレス記載が不要となる。
                q=query,
                labelIds=labels,
                maxResults=min(500, max_results - len(refs)),
                pageToken=page_token,
            )
        )
        response = request.execute()
        refs.extend(response.get("messages", []))

        if len(refs) >= max_results:
            break
        page_token = response.get("nextPageToken")
        if not page_token:
            break

    return refs[:max_results]


def fetch_full_messages(service, refs: Sequence[Dict[str, str]]) -> List[GmailMessage]:
    """list_message_refsで得たIDを用い、各メッセージの詳細情報を取得して構造化する。"""
    messages: List[GmailMessage] = []
    for ref in refs:
        raw = (
            service.users()
            .messages()
            .get(userId="me", id=ref["id"], format="full") # OAuth認証済みユーザー自身を指定。
            .execute()
        )
        messages.append(transform_raw_message(raw))
    return messages


def transform_raw_message(raw: Dict[str, object]) -> GmailMessage:
    """Gmail APIの生レスポンスを内部使用のGmailMessageへ変換する。"""
    payload = raw.get("payload", {}) or {}
    headers = normalize_headers(payload.get("headers", []))
    body = extract_body(payload)
    date_headers = headers.get("Date", [])
    internal_date = to_iso_datetime(
        raw.get("internalDate"), date_headers[0] if date_headers else None
    )

    return GmailMessage(
        id=str(raw.get("id")),
        thread_id=str(raw.get("threadId")),
        label_ids=raw.get("labelIds", []),
        snippet=str(raw.get("snippet", "")),
        internal_date=internal_date,
        headers=headers,
        body=body,
    )


def normalize_headers(headers: Iterable[Dict[str, str]]) -> Dict[str, List[str]]:
    """各キーにリストを紐づけて辞書化する。"""
    normalized: Dict[str, List[str]] = {}
    for header in headers:
        name = header.get("name")
        value = header.get("value")
        if name and value:
            normalized.setdefault(name, []).append(value)
    return normalized


def extract_body(payload: Dict[str, object]) -> str:
    """マルチパートに対応しつつメッセージ本文のプレーンテキストを抽出する。"""
    mime_type = payload.get("mimeType", "")
    body = payload.get("body", {}) or {}

    if isinstance(mime_type, str) and mime_type.startswith("multipart/"):
        for part in payload.get("parts", []) or []:
            text = extract_body(part)
            if text:
                return text
        return ""

    data = body.get("data")
    if not data:
        return ""

    return decode_body(str(data))


def decode_body(data: str) -> str:
    """URL-safe Base64エンコードされた本文データをUTF-8文字列へデコードする。"""
    padded = data + "=" * (-len(data) % 4)
    raw = base64.urlsafe_b64decode(padded.encode("utf-8"))
    return raw.decode("utf-8", errors="replace")


def to_iso_datetime(internal_date_value, header_date: Optional[str]) -> str:
    """ヘッダor内部日時からUTC ISO8601文字列を組み立てる。"""
    if header_date:
        try:
            parsed = date_parser.parse(header_date)
            return parsed.astimezone(timezone.utc).isoformat()
        except (ValueError, TypeError, OverflowError):
            pass
    try:
        timestamp_ms = int(str(internal_date_value))
        dt = datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc)
        return dt.isoformat()
    except (ValueError, TypeError):
        return datetime.now(timezone.utc).isoformat()


def dump_messages(messages: Sequence[GmailMessage], output_path: Optional[Path]) -> None:
    """取得したメッセージ配列をJSON化し、ファイルまたは標準出力へ書き出す。"""
    bundle = [message.to_dict() for message in messages]
    json_text = json.dumps(bundle, ensure_ascii=False, indent=2)
    if output_path:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json_text, encoding="utf-8")
    else:
        print(json_text)


def main() -> int:
    """CLI実行時のエントリポイント。認証→取得→出力の順に処理する。"""
    args = parse_args()
    try:
        creds = load_credentials(
            args.credentials,
            args.token,
            use_keyring=args.use_keyring,
            keyring_service=args.keyring_service,
            keyring_username=args.keyring_username,
            use_credentials_keyring=args.use_credentials_keyring,
            credentials_keyring_service=args.credentials_keyring_service,
            credentials_keyring_username=args.credentials_keyring_username,
        )
    except FileNotFoundError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    try:
        service = build("gmail", "v1", credentials=creds, cache_discovery=False)
        refs = list_message_refs(
            service,
            query=args.query,
            labels=args.labels,
            max_results=args.max_results,
        )
        messages = fetch_full_messages(service, refs)
        dump_messages(messages, args.output)
    except HttpError as exc:
        print(f"Gmail APIの呼び出しに失敗しました: {exc}", file=sys.stderr)
        return 2

    return 0


if __name__ == "__main__":
    sys.exit(main())
