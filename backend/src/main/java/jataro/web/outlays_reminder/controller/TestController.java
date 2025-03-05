package jataro.web.outlays_reminder.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jataro.web.outlays_reminder.entity.Outlay;
import jataro.web.outlays_reminder.repository.OutlayRepository;

@RestController
public final class TestController {
	
	@Autowired
	private OutlayRepository outlayRepository;
	
	private TestController() {}
	
	@GetMapping("/test")
	public ResponseEntity<Map<String, Object>> getSampleJson(){
		final Map<String, Object> jsonResponse = new HashMap<>();
		jsonResponse.put("message", "Hello, World!");
		jsonResponse.put("status", "success");
		jsonResponse.put("data", new String[] {"item1", "item2", "item3"});
		System.out.println(jsonResponse);
		return ResponseEntity.ok(jsonResponse);
	}
	
	@PostMapping("/create")
	public ResponseEntity<?> createOutlay(@RequestBody Map<String, Object> requestBody) {
		final String item = (String) requestBody.get("item");
		final Integer amount = (Integer) requestBody.get("amount");
		
		try {
			
			Outlay outlay = Outlay.create(item, amount);
			Outlay savedOutlay = outlayRepository.save(outlay);
			return ResponseEntity.ok(savedOutlay);
			
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
}
