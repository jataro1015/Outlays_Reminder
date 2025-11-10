# Gmail取得スクリプト

## セットアップ
1. `backend/scripts` で `pipx install poetry` などを使って Poetry を導入する。
2. `poetry install` を実行し、`google-api-python-client` などの依存関係を取得する。
3. Google Cloud Console で Gmail API を有効化し、OAuth クライアントを作成して `credentials.json` をこのディレクトリに配置する（Keyring利用時は後述）。

## 基本的な使い方
PowerShell から実行する場合は、バッククォート `` ` `` を行継続記号として使う。

```powershell
poetry run python -m gmail_fetcher `
  --query 'subject:ご利用金額確定' `
  --max-results 10 `
  --output outputs/out.json
```

- 初回実行時はブラウザで OAuth 認可を済ませると `token.json` が保存される。
- `--labels` でラベルを複数指定可能。
- `--output` を省略すると標準出力に JSON が出力される。

## Keyringを使った安全な保管
リフレッシュトークンやOAuthクライアント情報をWindows資格情報マネージャー等に保存したい場合は、Keyringオプションを利用する。

```powershell
poetry run python -m gmail_fetcher `
  --use-keyring `
  --keyring-service outlays-reminder-gmail-token `
  --keyring-username gmail-token `
  --use-credentials-keyring `
  --credentials-keyring-service outlays-reminder-gmail-client `
  --credentials-keyring-username gmail-client `
  --query 'subject:利用者仮登録完了のお知らせ' `
  --max-results 10 `
  --output outputs/out.json
```

- `--use-keyring` は `token.json` の代わりにKeyringへ資格情報を保存する。
- `--use-credentials-keyring` は `credentials.json` をKeyringから読み出す。OAuthクライアント情報をKeyringへ登録すればファイルレスで運用可能。
- サービス名やユーザー名は引数のほか、環境変数 `OUTLAYS_GMAIL_KEYRING_SERVICE` や `OUTLAYS_GMAIL_CREDENTIALS_KEYRING_SERVICE` などで指定できる。

### Keyringへの登録例
既存の `token.json` / `credentials.json` をKeyringへ移すには以下のPowerShellスクリプトが使える（`backend/scripts` ディレクトリで実行）。

```powershell
$script = @'
import pathlib, keyring

token_json = pathlib.Path("token.json").read_text(encoding="utf-8")
keyring.set_password("outlays-reminder-gmail-token", "gmail-token", token_json)

cred_json = pathlib.Path("credentials.json").read_text(encoding="utf-8")
keyring.set_password("outlays-reminder-gmail-client", "gmail-client", cred_json)
'@
$script | poetry run python -
```

Keyringに登録後は `token.json` / `credentials.json` を削除しても実行可能（`--use-keyring` / `--use-credentials-keyring` を忘れずに指定すること）。
