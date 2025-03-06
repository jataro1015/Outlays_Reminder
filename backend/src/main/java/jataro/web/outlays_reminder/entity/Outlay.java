package jataro.web.outlays_reminder.entity;

import java.time.temporal.ValueRange;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outlays") // テーブル名をoutlaysに指定
@JsonPropertyOrder({"item", "amount"}) // JSONシリアライズ時のプロパティの順序を保証する
@Getter
@Setter
public final class Outlay {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	
	@Lob // JSON型カラムをStringで扱う場合はこれを付与
	private String outlayData; // カラム名 outlay_dataに対応
	
	/**
	 * JPA（Hibernate）のためにのみ使用するpublic デフォルトコンストラクタであり、通常使用は推奨されません。
	 * アプリケーションコードからは、代わりに {@link #create(String, Integer)} static ファクトリメソッドを使用してください。
	 * 
	 * @deprecated アプリケーションコードから直接呼び出さないでください。代わりに {@link #create(String, Integer)} を使用してください。
	 */
	@Deprecated
	public Outlay() {}
	
	private Outlay(final String outlayData) {
		this.outlayData = outlayData;
	}
	
	public static Outlay create(final String item, final Integer amount) {
		if(!StringUtils.hasText(item)) {
			throw new IllegalArgumentException("費目には、1文字以上の空白以外の文字を必ず入力してください。");
		}
		if(amount == null || amount <= 0) {
			throw new IllegalArgumentException("金額は1以上の数値を入力してください。");
		}
		if(!ValueRange.of(amount, amount).isIntValue()) {
			throw new IllegalArgumentException("もう少し小さい数値を入力してください。");
		}
		
		//JSON 形式のデータを作成(Jackson)
		final var om = new ObjectMapper();
		final Map<String, Object> outlayDataMap = new LinkedHashMap<>();
		outlayDataMap.put("item", item);
		outlayDataMap.put("amount", amount);
		String outlayDataJson;
		try {
			outlayDataJson = om.writeValueAsString(outlayDataMap);
		}catch(JsonProcessingException jpe) {
			throw new IllegalArgumentException("JSONデータ作成に失敗しました。", jpe);
		}
		
		return new Outlay(outlayDataJson);
	}
}
