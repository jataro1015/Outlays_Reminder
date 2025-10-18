package jataro.web.outlays_reminder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class OutlayRequest {

  @NotBlank(message = "item must not be blank")
  @Size(max = 50, message = "item must be 50 characters or less")
  private String item;

  @NotNull(message = "amount is required")
  @Min(value = 0, message = "amount must be at least 0")
  @Max(value = 1000000, message = "amount must be 1,000,000 or less")
  private Integer amount;

  public void setItem(final String item) {
    this.item = item == null ? null : item.trim();
  }
}
