# backend/scripts

Gmail から支出に関するメールを取得して JSON 化し、必要に応じてバックエンド API へ連携するためのスクリプト群です。Poetry で管理しているため、初回は以下のセットアップを行ってください。

```powershell
cd backend/scripts
pipx install poetry   # または任意の方法でPoetryを導入
poetry install
```

---

## 設定ファイル (`config.yml`)

`config.example.yml` をコピーして `config.yml` を作成します。トップレベルに `gmail_profiles` と `ingest_profiles` を持ち、それぞれのプロファイルを `--profile` で切り替えて利用します。

```yaml
gmail_profiles:
  default:
    query: "subject:ご利用金額確定"
    max_results: 10
    output: "outputs/out.json"
    use_keyring: true
    keyring_service: "outlays-reminder-gmail-token"
    keyring_username: "gmail-token"
    use_credentials_keyring: true
    credentials_keyring_service: "outlays-reminder-gmail-client"
    credentials_keyring_username: "gmail-client"

ingest_profiles:
  default:
    input_json: "outputs/out.json"
    api_base_url: "http://localhost:8080/api/v1/outlays"
    headers:
      Content-Type: "application/json"
    item_field: "subject"      # subject / snippet / body
    amount_source: "body"      # subject / snippet / body
    amount_pattern: "([0-9,]+)円"
    min_amount: 1
    skip_without_amount: true
    dry_run: false
```

`config.yml` / `config.yaml` は `.gitignore` 済みなので、環境ごとに自由に編集してください。

---

## Gmail 取得スクリプト (`gmail_fetcher.py`)

### 実行例

```powershell
poetry run python -m gmail_fetcher `
  --config config.yml `
  --profile default
```

主なオプション:

| オプション | 概要 |
| ---------- | ---- |
| `--query` / `--labels` | Gmail の検索条件を直接指定。設定ファイルがあれば自動適用。 |
| `--output` | JSONの保存先。デフォルトは `outputs/out.json`。 |
| `--use-keyring` | `token.json` の代わりに Keyring (Windows 資格情報マネージャー 等) に保存。 |
| `--use-credentials-keyring` | `credentials.json` を Keyring から読み込む。 |

### Keyring 登録

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

登録後は `--use-keyring --use-credentials-keyring` を指定すればファイルレスで実行できます。

### PowerShell ラッパー

Poetry コマンドを毎回入力したくない場合は `run_gmail_fetcher.ps1` を利用してください。

```powershell
.\run_gmail_fetcher.ps1 -Config "config.yml" -Profile "default"
```

### タスクスケジューラ登録

1. タスクスケジューラで新しいタスクを作成。  
2. 「操作」 → プログラム `powershell`, 引数:
   ```
   -NoLogo -NoProfile -ExecutionPolicy Bypass `
     -File "C:\path\to\backend\scripts\run_gmail_fetcher.ps1" `
     -Config "C:\path\to\backend\scripts\config.yml" `
     -Profile "default"
   ```
3. 「開始場所」を `backend/scripts` に設定し、任意のトリガーを追加。  
4. 初回は手動実行で OAuth 認可と Keyring 登録を済ませてください。

---

## 月次 Gmail プロファイル生成スクリプト (`monthly_gmail_config_builder.py`)
毎日 1 回のバッチなどで「当月1日〜末日（= 翌月1日未満）」のメールを取得したい場合は、`gmail_fetcher.py` 実行前に本スクリプトで `config.yml` を更新してください。`gmail_profiles.one_month.query` のベース条件（例: `subject:金額`）から `after:{当月1日}` `before:{翌月1日}` を自動付与します。
```powershell
poetry run python monthly_gmail_config_builder.py `
  --config config.yml `
  --profile one_month `
  --base-query "subject:金額"
```
- `--base-query` を省略すると、直前の `query` から `after/before` 句を除去したものを再利用します。
- `--dry-run` を付けると書き込みを行わず、生成されるクエリだけを確認できます。
- Gmail の `before:` は終端日を含まないため、翌月1日を指定しても当月末日 23:59:59 までが対象になります。
タスクスケジューラ等で自動化する場合は「`monthly_gmail_config_builder.py` → `gmail_fetcher.py --profile one_month`」の順で実行してください。

---

## Outlay API 連携スクリプト (`import_outlays.py`)

`gmail_fetcher` が出力した JSON を読み取り、バックエンドの `/api/v1/outlays` へ自動登録します。

### 実行例

```powershell
poetry run python -m import_outlays `
  --config config.yml `
  --profile default
```

主な機能:

- `ingest_profiles` の `input_json` から Gmail メール一覧を取得。
- 件名/本文などから `item` を生成し、正規表現 (`amount_pattern`) で金額を抽出。
- `--dry-run` で API を呼ばずに動作確認。
- `--limit` で先頭 N 件のみ処理。
- 失敗した API 呼び出しはステータスとレスポンス本文を標準エラーへ出力。

CLI オプションで `--input`, `--api-base-url`, `--dry-run`, `--limit` を上書きできます。

---

## 推奨ワークフロー

1. `poetry run python -m gmail_fetcher --profile default` で最新メールを `outputs/out.json` に書き出す。
2. 内容を確認後、`poetry run python -m import_outlays --profile default` を実行してバックエンドへ連携。必要なら `--dry-run` で事前確認。
3. タスクスケジューラ等で `run_gmail_fetcher.ps1` と `import_outlays.py` を組み合わせれば、完全自動化も可能です。

---

## トラブルシューティング

- **PyYAML が未導入:** `poetry install` を再実行してください。
- **Gmail API の認可でブラウザが開かない:** Windows の既定ブラウザ設定を確認のうえ、`--noauth_local_webserver` を使うなど環境に応じて調整してください。
- **Outlay API 連携で 401/403 が返る:** `headers` に必要な認証トークンを設定しているか確認してください。

質問や改善案があれば Issue / PR で気軽に連絡してください。
