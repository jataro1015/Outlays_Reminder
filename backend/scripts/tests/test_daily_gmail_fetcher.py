from __future__ import annotations

import sys
from datetime import date, timedelta
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from daily_gmail_fetcher import determine_output_path  # noqa: E402


def test_determine_output_path_default_when_no_template() -> None:
    """正常系: テンプレート未指定時はデフォルトパスが返る"""
    # Given: テンプレートが指定されていない
    start = date(2025, 3, 27)
    end = start + timedelta(days=1)

    # When: determine_output_path を呼ぶ
    result = determine_output_path(None, "default", start, end)

    # Then: outputs/gmail_{profile}_{YYYYMMDD}.json 形式のデフォルトパスになる
    assert result == Path("outputs/gmail_default_20250327.json")


def test_determine_output_path_expands_profile_and_date_placeholders() -> None:
    """正常系: {profile} と {date} プレースホルダーが正しく展開される"""
    # Given: {profile} と {date} を含むテンプレート
    template = "outputs/gmail_{profile}_{date}.json"
    start = date(2025, 3, 27)
    end = start + timedelta(days=1)

    # When: テンプレートを渡して呼ぶ
    result = determine_output_path(template, "myprofile", start, end)

    # Then: プレースホルダーが展開されたパスになる
    assert result == Path("outputs/gmail_myprofile_20250327.json")


def test_determine_output_path_expands_start_and_end_placeholders() -> None:
    """正常系: {start} と {end} プレースホルダーが正しく展開される"""
    # Given: {start} と {end} を含むテンプレート
    template = "outputs/{profile}_{start}_{end}.json"
    start = date(2025, 3, 1)
    end = date(2025, 3, 3)

    # When: range_days=2 相当の start/end を渡して呼ぶ
    result = determine_output_path(template, "default", start, end)

    # Then: start と end がそれぞれ展開される
    assert result == Path("outputs/default_20250301_20250303.json")


def test_determine_output_path_uses_config_template() -> None:
    """正常系(P2修正確認): configから渡されたテンプレートが適用される"""
    # Given: config.yml の daily_output_template から渡されたテンプレート
    config_template = "outputs/daily_{profile}_{date}.json"
    start = date(2025, 11, 23)
    end = start + timedelta(days=1)

    # When: config由来のテンプレートを引数として渡す
    result = determine_output_path(config_template, "default", start, end)

    # Then: config のテンプレートが展開されたパスになる
    assert result == Path("outputs/daily_default_20251123.json")


def test_determine_output_path_cli_template_takes_priority_over_config() -> None:
    """正常系: CLIテンプレートがconfigテンプレートより優先される"""
    # Given: CLI引数とconfig由来の両テンプレートが存在する
    cli_template = "outputs/cli_{profile}_{date}.json"
    config_template = "outputs/config_{profile}_{date}.json"
    start = date(2025, 3, 27)
    end = start + timedelta(days=1)

    # When: CLI引数テンプレートを優先して渡す (args.output_template or config_template)
    result = determine_output_path(cli_template or config_template, "default", start, end)

    # Then: CLI引数のテンプレートが使われる
    assert result == Path("outputs/cli_default_20250327.json")
