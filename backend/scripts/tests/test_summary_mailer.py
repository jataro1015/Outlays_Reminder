from __future__ import annotations

import json
import sys
from datetime import date
from pathlib import Path

import pytest

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from summary_mailer import SummaryMailSettings, compute_monthly_diff  # noqa: E402


def build_settings(state_file: Path | None, target: date) -> SummaryMailSettings:
    """テスト用 SummaryMailSettings を生成するヘルパー。"""
    return SummaryMailSettings(
        input_json=Path("outputs/imported_outlays.json"),
        recipient="test@example.com",
        sender="noreply@example.com",
        subject="test",
        dry_run=True,
        smtp_host="localhost",
        smtp_port=25,
        smtp_username=None,
        smtp_password=None,
        smtp_use_tls=False,
        smtp_use_ssl=False,
        smtp_timeout=5,
        profile_name="default",
        state_file=state_file,
        target_date=target,
    )


def write_state(path: Path, payload: dict) -> None:
    """stateファイルにJSONを書き込む。"""
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def test_compute_monthly_diff_accumulates_within_month(tmp_path: Path) -> None:
    """正常系: 同月で実行した際に月次累積が加算される"""
    # Given: 同じ月の累積が state に存在する
    state_file = tmp_path / "state.json"
    write_state(
        state_file,
        {"profiles": {"default": {"year_month": "2025-03", "count": 5, "amount": 10000}}},
    )
    settings = build_settings(state_file, date(2025, 3, 27))

    # When: 追加で 3 件 / 1500 円 を渡す
    diff = compute_monthly_diff(settings, count=3, total_amount=1500)

    # Then: 差分は累積8件/11500円となり、state_payloadにも反映される
    assert diff.diff_count == 8
    assert diff.diff_amount == 11500
    saved = diff.state_payload["profiles"]["default"]
    assert saved["count"] == 8 and saved["amount"] == 11500


def test_compute_monthly_diff_resets_on_new_month(tmp_path: Path) -> None:
    """正常系: 月が変わったら累積がリセットされる"""
    # Given: stateが前月(2025-02)のまま残っている
    state_file = tmp_path / "state.json"
    write_state(
        state_file,
        {"profiles": {"default": {"year_month": "2025-02", "count": 20, "amount": 5000}}},
    )
    settings = build_settings(state_file, date(2025, 3, 1))

    # When: 新しい月(2025-03)で件数2/金額500を追加
    diff = compute_monthly_diff(settings, count=2, total_amount=500)

    # Then: 差分はリセットされ 2件/500円 がそのまま月次累積になる
    assert diff.diff_count == 2
    assert diff.diff_amount == 500
    saved = diff.state_payload["profiles"]["default"]
    assert saved["year_month"] == "2025-03"


def test_compute_monthly_diff_initializes_without_state(tmp_path: Path) -> None:
    """正常系: 初回実行でstateが無い場合に初期化される"""
    # Given: stateファイルが存在しない(初回実行)
    state_file = tmp_path / "state.json"
    settings = build_settings(state_file, date(2025, 3, 5))

    # When: 4件/900円のデータを渡す
    diff = compute_monthly_diff(settings, count=4, total_amount=900)

    # Then: 差分と累積がともに 4件/900円 で初期化される
    assert diff.diff_count == 4
    assert diff.diff_amount == 900
    saved = diff.state_payload["profiles"]["default"]
    assert saved["count"] == 4 and saved["amount"] == 900


def test_compute_monthly_diff_keeps_totals_when_zero_increment(tmp_path: Path) -> None:
    """正常系: 当日分が0でも月次累積は前回値のまま"""
    # Given: 既に 5件/1000円 の累積がある
    state_file = tmp_path / "state.json"
    write_state(
        state_file,
        {"profiles": {"default": {"year_month": "2025-03", "count": 5, "amount": 1000}}},
    )
    settings = build_settings(state_file, date(2025, 3, 10))

    # When: 当日の件数・金額が 0
    diff = compute_monthly_diff(settings, count=0, total_amount=0)

    # Then: 差分は前回累積の 5件/1000円 のまま表示される
    assert diff.diff_count == 5
    assert diff.diff_amount == 1000


def test_compute_monthly_diff_raises_when_state_file_missing() -> None:
    """異常系: state_file未設定なら ValueError を送出"""
    # Given: state_file が設定されていない
    settings = build_settings(None, date(2025, 3, 1))

    # When / Then: ValueError が送出される
    with pytest.raises(ValueError):
        compute_monthly_diff(settings, count=1, total_amount=100)


def test_compute_monthly_diff_raises_on_invalid_json(tmp_path: Path) -> None:
    """異常系: state JSON破損時に JSONDecodeError が発生"""
    # Given: stateファイルのJSONが破損している
    state_file = tmp_path / "state.json"
    state_file.write_text("{invalid json", encoding="utf-8")
    settings = build_settings(state_file, date(2025, 3, 1))

    # When / Then: JSONDecodeError が発生
    with pytest.raises(json.JSONDecodeError):
        compute_monthly_diff(settings, count=1, total_amount=100)


def test_compute_monthly_diff_handles_month_boundary_sequence(tmp_path: Path) -> None:
    """境界値系: 月末→翌月の連続実行で累積とリセットが挙動通りとなる"""
    # Given: stateに 2025-03 の累積 10件/2000円 が記録されている
    state_file = tmp_path / "state.json"
    write_state(
        state_file,
        {"profiles": {"default": {"year_month": "2025-03", "count": 10, "amount": 2000}}},
    )
    march_settings = build_settings(state_file, date(2025, 3, 31))

    # When: 3/31 分 (2件/600円) を反映
    march_diff = compute_monthly_diff(march_settings, count=2, total_amount=600)

    # Then: 差分は 12件/2600円 となる
    assert march_diff.diff_count == 12
    assert march_diff.diff_amount == 2600

    # When: state_payload を実際のファイルに書き戻し、翌月4/1 を処理
    write_state(state_file, march_diff.state_payload)
    april_settings = build_settings(state_file, date(2025, 4, 1))
    april_diff = compute_monthly_diff(april_settings, count=1, total_amount=300)

    # Then: 新しい月として 1件/300円 から再スタートする
    assert april_diff.diff_count == 1
    assert april_diff.diff_amount == 300
    saved = april_diff.state_payload["profiles"]["default"]
    assert saved["count"] == 1 and saved["amount"] == 300
