# Gmail取得スクリプト

## セットアップ
1. `backend/scripts` で `pipx install poetry` などを使って Poetry を導入する。
2. `poetry install` を実行し、`google-api-python-client` などの依存関係を取得する。
3. Google Cloud Console で Gmail API を有効化し、OAuth クライアントを作成して `credentials.json` をこのディレクトリに配置する（Keyring利用時は後述）。
4. 設定ファイルが必要な場合は `config.example.yml` を `config.yml` にコピーして編集する。

## 設定ファイルとCLI
`config.yml` では複数プロファイルを定義でき、`--config` / `--profile` で切り替えます。`config.yml` が存在すれば `--config` 省略時でも自動的に読み込まれます。

```powershell
poetry run python -m gmail_fetcher `
  --config config.yml `
  --profile default
```

設定ファイルでは以下のキーを利用できます。

| キー | 説明 |
| --- | --- |
| `query` | Gmail検索クエリ (`subject:...`, `from:...` など) |
| `labels` | ラベル名の配列 |
| `max_results` | 取得件数 |
| `output` | 保存先パス (例: `outputs/out.json`) |
| `use_keyring`, `keyring_service`, `keyring_username` | トークンのKeyring設定 |
| `use_credentials_keyring`, `credentials_keyring_service`, `credentials_keyring_username` | OAuthクライアント情報のKeyring設定 |
| `credentials`, `token` | ファイルパスを直接指定する場合 |

## Keyringを使った安全な保管
リフレッシュトークンやOAuthクライアント情報をWindows資格情報マネージャー等に保存したい場合は、Keyringオプションを利用します。

```powershell
poetry run python -m gmail_fetcher `
  --config config.yml `
  --profile default `
  --use-keyring `
  --use-credentials-keyring
```

- `--use-keyring` は `token.json` の代わりにKeyringへ資格情報を保存する。
- `--use-credentials-keyring` は `credentials.json` をKeyringから読み出す。サービス／ユーザー名は設定ファイルまたは環境変数 `OUTLAYS_GMAIL_KEYRING_SERVICE` などで上書き可能。

### Keyringへの登録例
既存の `token.json` / `credentials.json` をKeyringへ移すには以下のPowerShellスクリプトが使えます（`backend/scripts` ディレクトリで実行）。

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

Keyringに登録後は `token.json` / `credentials.json` を削除しても実行可能（`--use-keyring` / `--use-credentials-keyring` を忘れずに指定してください）。

## PowerShellラッパー
Poetryコマンドを毎回書くのが面倒な場合は `run_gmail_fetcher.ps1` を利用できます。

```powershell
.\run_gmail_fetcher.ps1 -Config "config.yml" -Profile "default"
```

追加のCLIオプションを渡したい場合は `-ExtraArgs @("--labels", "カード明細")` のように指定します。

## Windowsタスクスケジューラでの定期実行
1. タスクスケジューラで新しいタスクを作成する。
2. 「操作」にて  
   - プログラム/スクリプト: `powershell`  
   - 引数:  
     ```
     -NoLogo -NoProfile -ExecutionPolicy Bypass `
       -File "C:\path\to\backend\scripts\run_gmail_fetcher.ps1" `
       -Config "C:\path\to\backend\scripts\config.yml" `
       -Profile "default"
     ```
   - 開始場所: `C:\path\to\backend\scripts`
3. 「トリガー」で任意のスケジュール（例: 毎日朝9時）を設定。
4. 事前に手動実行してOAuthブラウザ認可／Keyring登録を済ませておく。

## 直接CLIで実行する場合の例
```powershell
poetry run python -m gmail_fetcher `
  --query 'subject:ご利用金額確定' `
  --max-results 10 `
  --output outputs/out.json
```

- 初回実行時はブラウザで OAuth 認可を済ませると `token.json` が保存される。
- `--labels` でラベルを複数指定可能。
- `--output` を省略すると標準出力に JSON が出力される。
