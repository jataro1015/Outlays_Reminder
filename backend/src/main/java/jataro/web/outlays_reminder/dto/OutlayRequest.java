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
	
	@NotBlank(message = "費目は必須です")
	@Size(min = 1, max = 50, message = "費目は1～50文字で入力してください。")
	private String item;
	
	@NotNull(message = "金額は必須です")
	@Min(value = 0, message = "金額は1円以上で入力してください。")
	@Max(value = 1000000, message = "金額は1000000円以下で入力してください。")
	private Integer amount;
	
}
