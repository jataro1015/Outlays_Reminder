package jataro.web.outlays_reminder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.util.HtmlUtils;

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
   * JPA（Hibernate）のためにのみ使用するpublic デフォルトコンストラクタであり、通常使用は推奨されません。 アプリケーションコードからは、代わりに {@link
   * #create(String, Integer)} static ファクトリメソッドを使用してください。
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
    if (item == null) {
      throw new IllegalArgumentException("item must not be null");
    }

    final String trimmedItem = item.trim();
    if (trimmedItem.isEmpty()) {
      throw new IllegalArgumentException("item must not be blank");
    }

    if (amount == null) {
      throw new IllegalArgumentException("amount must not be null");
    }

    if (amount < 0) {
      throw new IllegalArgumentException("amount must be greater than or equal to 0");
    }

    if (amount > 1_000_000) {
      throw new IllegalArgumentException("amount must be less than or equal to 1000000");
    }

    final String escapedItem = HtmlUtils.htmlEscape(trimmedItem, StandardCharsets.UTF_8.name());

    return new Outlay(escapedItem, amount);
  }
}
