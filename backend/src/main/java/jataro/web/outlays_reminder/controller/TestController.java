package jataro.web.outlays_reminder.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class TestController {
	
	private TestController() {}
	
	@GetMapping("/test")
	public ResponseEntity<Map<String, Object>> getSampleJson(){
		final Map<String, Object> jsonResponse = new HashMap<>();
		jsonResponse.put("message", "Hello, World!");
		jsonResponse.put("status", "success");
		jsonResponse.put("data", new String[] {"item1", "item2", "item3"});
		return ResponseEntity.ok(jsonResponse);
	}
}
