package jataro.web.outlays_reminder.entity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ValueRange;

import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outlays") // テーブル名をoutlaysに指定
@Getter
@Setter
public final class Outlay {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	
	@Column(nullable = false)
	private String item;
	
	@Column(nullable = false)
	private Integer amount;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	/**
	 * JPA（Hibernate）のためにのみ使用するpublic デフォルトコンストラクタであり、通常使用は推奨されません。
	 * アプリケーションコードからは、代わりに {@link #create(String, Integer)} static ファクトリメソッドを使用してください。
	 * 
	 * @deprecated アプリケーションコードから直接呼び出さないでください。代わりに {@link #create(String, Integer)} を使用してください。
	 */
	@Deprecated
	public Outlay() {}
	
	private Outlay(final String item, Integer amount) {
		this.item = item;
		this.amount = amount;
		this.createdAt = LocalDateTime.now();
	}
	
	public static Outlay create(final String item, final Integer amount) {
        validateItem(item);
        validateAmount(amount);
		
	    final String escapedItem = 
	    		HtmlUtils.htmlEscape(item, StandardCharsets.UTF_8.name());
	    
        return new Outlay(escapedItem, amount);
	}
	
    private static void validateItem(final String item) {
        if(!StringUtils.hasText(item)) {
            throw new IllegalArgumentException("費目には、1文字以上の文字を入力してください。空白のみの入力は許可されません。");
        }
        if (item.length() > 50) {
            throw new IllegalArgumentException("費目は50文字以内で入力してください。");
        }
    }

    private static void validateAmount(final Integer amount) {
        if(amount == null || !ValueRange.of(0, 1000000).isValidValue(amount)) {
            throw new IllegalArgumentException("金額は、0円～100万円の範囲で入力してください。");
        }
    }
}
