package jataro.web.outlays_reminder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class OutlayRequest {
	
	@NotBlank(message = "費目は必須です")
	private String item;
	
	@NotNull(message = "金額は必須です")
	@Min(value = 1, message = "金額は1以上である必要があります")
	private Integer amount;
	
}
