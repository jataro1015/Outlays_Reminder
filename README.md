# Outlays_Reminder

Outlays_Reminder は、Spring Boot + React で構築された支出管理アプリケーションです。メール経由で届くカード利用通知などを Python スクリプトで取得し、バックエンド API に自動登録できるようになっています。

---

## アーキテクチャ概要

| レイヤ | 技術スタック | 概要 |
| ------ | ------------ | ---- |
| Backend | Java 21 / Spring Boot 3.3 / Spring Data JPA / H2 DB | `backend/` 以下。REST API を提供し、`outlays` テーブルへ永続化します。 |
| Frontend | React 19 (CRA) / Fetch API | `frontend/` 以下。支出一覧・登録・更新・削除 UI を提供します。 |
| Scripts | Python 3.11 / Poetry / Gmail API / Keyring / PyYAML / requests | `backend/scripts/` 以下。Gmail の抽出と API 連携の CLI を含みます。 |

---

## バックエンド (`backend/`)

### ドメインモデル

| フィールド | 型 | 概要 |
| ---------- | -- | ---- |
| `id` | `Integer` | IDENTITY で自動採番。 |
| `item` | `String` | 必須。DTO+エンティティでトリムや HTML エスケープを実施。 |
| `amount` | `Integer` | 必須。0～1,000,000 の範囲で検証。 |
| `createdAt` | `LocalDateTime` | 登録時刻を自動設定。 |

### REST API

| Method | Path | 説明 |
| ------ | ---- | ---- |
| POST | `/api/v1/outlays` | `{"item","amount"}` を受け取り登録。`201 Created` と ID を返す。 |
| GET | `/api/v1/outlays` | 全件取得。`sortBy`/`sortDirection` で並び替え。 |
| GET | `/api/v1/outlays/{id}` | ID 指定で取得。 |
| GET | `/api/v1/outlays/on-date?date=YYYY-MM-DD` | `createdAt` の日付で絞り込み。 |
| PUT | `/api/v1/outlays/{id}` | DTO を用いた更新。 |
| DELETE | `/api/v1/outlays/{id}` | 削除。成功時 `204 No Content`。 |

- 全エンドポイントに `@CrossOrigin` を付与し、`localhost:3000`（React）から直接呼べます。
- `jakarta.validation` と `Outlay.create` の二段で入力検証を実施。
- 日付検索は `OutlayRepository#findByCreatedAtDate` を使用。
- H2 (TCP) を標準構成とし、`application.properties` で接続設定を管理します（認証は未実装）。
`backend/src/main/resources/application-example.properties` をコピーして `application.properties` を作成し、必要な値をローカルで編集してください。

### テスト / Lint / 起動

```bash
cd backend
./gradlew :test            # REST API テスト
./gradlew spotlessApply    # Spotless (Google Java Format)
./gradlew bootRun          # Spring Boot を :8080 で起動
```

---

## フロントエンド (`frontend/`)

- Create React App 構成 (`npm start` で `localhost:3000`)。`package.json` の `proxy` により API は `localhost:8080` に転送。
- 主な構成要素
  - `OutlayForm`: `POST /api/v1/outlays` へ送信。入力チェックとエラーメッセージを表示。
  - `Controls`: ソート／ID／日付フィルタの入力フォーム。
  - `OutlayList`: テーブル表示＋更新／削除ボタン。
  - `useOutlays` フック: `outlays`,`loading`,`error`,`refetch` を管理。
  - `OutlayManager` / `Header`: UI 連携やテーマ切替を実装。

```bash
cd frontend
npm install
npm start
```

---

## Gmail / Outlay スクリプト (`backend/scripts/`)

- `gmail_fetcher.py`: Gmail API で指定クエリのメール本文・ヘッダーを JSON 出力。
- `import_outlays.py`: `gmail_fetcher` の JSON を解析し、`/api/v1/outlays` へ登録。正規表現で金額抽出、`--dry-run` で検証可。
- `config.yml` の `gmail_profiles` / `ingest_profiles` でプロファイルを管理し、`--config` / `--profile` で切替。
- Keyring（Windows 資格情報マネージャー等）に `token.json` / `credentials.json` を移して、`--use-keyring` / `--use-credentials-keyring` でファイルレス運用。
- PowerShell ラッパー `run_gmail_fetcher.ps1` で定期実行しやすい構成。
- 詳細手順（設定ファイル、Keyring、タスクスケジューラ、API 連携など）は `backend/scripts/README.md` を参照。

---

## 推奨セットアップフロー

1. **バックエンド**
   ```bash
   cd backend
   cp src/main/resources/application-example.properties src/main/resources/application.properties
   ./gradlew bootRun
   ```
   必要に応じて `application.properties`（H2 接続など）をローカルで調整。

2. **フロントエンド**
   ```bash
   cd frontend
   npm install
   npm start
   ```
   `http://localhost:3000` を開き、支出登録／検索／更新／削除を確認。

3. **Gmail / API 連携スクリプト（任意）**
   ```bash
   cd backend/scripts
   poetry install
   cp config.example.yml config.yml  # gmail_profiles / ingest_profiles を編集
   poetry run python -m gmail_fetcher --config config.yml --profile default
   poetry run python -m import_outlays --config config.yml --profile default
   ```
   - 初回は Gmail OAuth の認可が必要。
   - 定期運用は `run_gmail_fetcher.ps1` とタスクスケジューラを組み合わせる。

---

## 今後の拡張案

- 認証／ユーザー管理（現状シングルユーザー）。
- Gmail 取得から API 登録までの完全自動化。
- フロントエンド編集 UX 向上（モーダルやインライン編集など）。
- PostgreSQL など他 RDB への対応。

詳細は各 README やソースコードを参照してください。Issue や PR も歓迎します。
