package jataro.web.outlays_reminder.constants;

public final class ErrorMessages {

  private ErrorMessages() {}

  public static final String INVALID_INPUT = "入力値が不正です。";
  public static final String INVALID_REQUEST_BODY_TYPE = "リクエストボディの型を合わせてください。";

  public static final String OUTLAY_CREATION_FAILED = "出費データ登録中にエラーが発生しました。";
  public static final String OUTLAY_FETCH_ERROR = "出費データ取得中にエラーが発生しました。";
  public static final String OUTLAY_FETCH_BY_ID_ERROR = "ID指定による出費データ取得中にエラーが発生しました。";
  public static final String OUTLAY_FETCH_BY_DATE_ERROR = "日付指定による出費データ取得中にエラーが発生しました。";

  public static final String OUTLAYS_NOT_FOUND = "出費データが存在しません";
  public static final String OUTLAY_NOT_FOUND_BY_ID = "指定されたIDの出費データは存在しません。";
  public static final String OUTLAY_NOT_FOUND_BY_DATE = "指定された日付の出費データは存在しません。";

  public static final String INVALID_DATE_FORMAT = "日付の形式が正しくありません。YYYY-MM-DD の形式で入力してください。";
  public static final String REQUEST_PARAMETER_INVALID = "リクエストパラメータの形式が不正です。";

  public static final String OUTLAY_UPDATE_ERROR = "出費データ更新中にサーバーエラーが発生しました。";
  public static final String OUTLAY_DELETE_ERROR = "出費データ削除中にエラーが発生しました。";
}
