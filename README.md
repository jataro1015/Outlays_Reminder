# Outlays_Reminder

個人の支出情報を記録・検索・更新・削除できる Spring Boot ＋ React 構成のアプリケーションです。  
加えて、Gmail からカード利用通知などを JSON として取得する Python スクリプトを付属させ、支出登録を自動化できるようにしています。

---

## システム構成と主要技術

| レイヤ | 主な技術 | 補足 |
| ------ | -------- | ---- |
| Backend | Java 21 / Spring Boot 3.3 / Spring Data JPA / H2 DB | `backend/`。REST API を提供し、`outlays` テーブルへ永続化します。 |
| Frontend | React 19 (CRA) / Fetch API | `frontend/`。支出の一覧・登録・更新・削除 UI と検索コントロールを提供します。 |
| Scripts | Python 3.11 / Poetry / Google Gmail API / Keyring | `backend/scripts/`。Gmail から指定条件のメールを JSON へ出力する CLI (`gmail_fetcher.py`) を含みます。 |

---

## バックエンド仕様 (`backend/`)

### ドメインモデル

| フィールド | 型 | 説明 / 制約 |
| ---------- | -- | ----------- |
| `id` | `Integer` | 自動採番 (IDENTITY)。 |
| `item` | `String` | 必須。HTML エスケープ済み。`OutlayRequest` では `@NotBlank` + 最大 50 文字。 |
| `amount` | `Integer` | 必須。0〜1,000,000。`Outlay.create` でも上限・下限を再検証。 |
| `createdAt` | `LocalDateTime` | 登録時刻を自動設定。 |

### API エンドポイント

| Method | Path | 説明 |
| ------ | ---- | ---- |
| `POST` | `/api/v1/outlays` | `{"item","amount"}` を受け取り新規登録。成功時 `201 Created`＋`{"id": ...}` を返却。 |
| `GET` | `/api/v1/outlays` | すべての支出を取得。`sortBy`（`item`,`amount`,`createdAt`,`id` 等）と `sortDirection`（`asc`/`desc`）で並び替え。 |
| `GET` | `/api/v1/outlays/{id}` | 指定 ID の支出を取得。 |
| `GET` | `/api/v1/outlays/on-date?date=YYYY-MM-DD` | `createdAt` の日付部分で絞り込み。 |
| `PUT` | `/api/v1/outlays/{id}` | `OutlayRequest` を用いて対象レコードを更新。 |
| `DELETE` | `/api/v1/outlays/{id}` | 対象レコードを削除。成功時 `204 No Content`。 |

- すべてのエンドポイントは `@CrossOrigin` でフロントエンド（デフォルト `localhost:3000`）から直接叩けます。
- 入力検証は `jakarta.validation` と `Outlay.create` の二層で実施し、エラー時は `ErrorMessages`（`backend/src/main/java/jataro/web/outlays_reminder/constants/ErrorMessages.java`）に定義したメッセージを返却。
- 日付検索は `OutlayRepository#findByCreatedAtDate`（`FORMATDATETIME` を使用）で実装。
- 認証は未実装。開発時はローカル H2 DB（`spring.datasource.url=jdbc:h2:tcp://localhost:9092/...`）を利用します。

### テスト

`backend/src/test/java/jataro/web/outlays_reminder/controller/OutlayControllerTest.java` に REST API の基本的な振る舞いをカバーするテストがあります。  
実行コマンド:

```bash
cd backend
./gradlew :test
```

### Lint / フォーマット

Java コードは Spotless（Google Java Format）で整えます。変更前後で以下を実行してください。

```bash
cd backend
./gradlew spotlessApply
```

### 起動

```bash
cd backend
./gradlew bootRun
```

---

## フロントエンド仕様 (`frontend/`)

- React 19（Create React App）構成。`npm start` で `localhost:3000` が立ち上がり、`package.json` の `proxy` により API 呼び出しは `localhost:8080` へ転送されます。
- 主なコンポーネント:
  - `OutlayForm` … `POST /api/v1/outlays` へ送信。必須チェックと API から返った詳細エラーメッセージの表示を実装。
  - `Controls` … 並び替え／ID・日付フィルタ条件を入力し、`useOutlays` の再取得をトリガー。
  - `OutlayList` … 取得データをテーブル表示。Update/ Delete ボタンを提供し、更新時は `window.prompt` で値を入力。
  - `useOutlays` … 取得状態（loading / error）や再取得のコールバックを一元管理。
  - `Header` / `OutlayManager` / テーマ切替など UI まわり。

### フロントエンド実行

```bash
cd frontend
npm install
npm start
```

---

## Gmail 取得スクリプト (`backend/scripts/`)

- `gmail_fetcher.py` は Gmail API を利用して指定クエリのメール本文・ヘッダーを JSON に保存します。
- `poetry install` で依存（`google-api-python-client`, `keyring`, `PyYAML` 等）を導入。
- `config.yml` に複数プロファイルを定義し、`--config` / `--profile` で CLI に適用可能。例は `config.example.yml` を参照。
- Keyring（Windows 資格情報マネージャー）にトークンや OAuth クライアント情報を登録し、`--use-keyring` / `--use-credentials-keyring` を指定するとファイルレスで運用できます。
- PowerShell ラッパー `run_gmail_fetcher.ps1` を用意。  
  例: `.\run_gmail_fetcher.ps1 -Config "config.yml" -Profile "card_statement"`
- `README`（`backend/scripts/README.md`）に Keyring への登録方法、タスクスケジューラ設定例、出力先 (`outputs/`) など詳細手順を記載済み。

---

## 包括的なセットアップ手順

1. **バックエンド**  
   - `cd backend && ./gradlew bootRun`  
   - 必要に応じて `application.properties` の H2 接続情報を変更。
2. **フロントエンド**  
   - 別ターミナルで `cd frontend && npm install && npm start`。  
   - ブラウザで `http://localhost:3000` を開き、支出の登録・検索・更新・削除を行う。
3. **Gmail スクリプト (任意)**  
   - `cd backend/scripts && poetry install`  
   - `config.example.yml` を `config.yml` にコピーして編集。  
   - 初回は `poetry run python -m gmail_fetcher --config config.yml --profile default` を実行してブラウザ認可を完了。  
   - 定期自動実行は `run_gmail_fetcher.ps1` をタスクスケジューラに登録。

---

## 今後の拡張アイデア

- 認証／ユーザー管理の導入（現在は単一ユーザー前提）。
- Gmail 取得スクリプトとバックエンド登録 API の連携自動化。
- フロントエンドでの編集 UI 改善（モーダルやインライン編集等）。
- H2 以外の RDB（PostgreSQL など）対応。

プロジェクト全体の詳細はそれぞれの README およびソースコードをご参照ください。質問や改善案があれば Issue / PR で歓迎します。
