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
	
	@NotBlank
	@Size(min = 1, max = 50)
	private String item;
	
	@NotNull
	@Min(value = 0)
	@Max(value = 1000000)
	private Integer amount;
	
}
