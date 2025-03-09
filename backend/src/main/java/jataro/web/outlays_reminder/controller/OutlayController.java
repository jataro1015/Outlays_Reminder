package jataro.web.outlays_reminder.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jataro.web.outlays_reminder.entity.Outlay;
import jataro.web.outlays_reminder.repository.OutlayRepository;

@RestController
@RequestMapping("/api/v1/outlays")
public final class OutlayController {
	
	@Autowired
	private OutlayRepository outlayRepository;
	
	private OutlayController() {}
	
	@PostMapping
	public ResponseEntity<?> 
	registerOutlay(@RequestBody final Map<String, Object> requestBody) {
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
	
	@GetMapping("/{id}")
	public ResponseEntity<?> 
	getOutlayById(@PathVariable("id") final Integer id){
		final Optional<Outlay> existingOutlay = outlayRepository.findById(id); // IDで既存の Outlay を検索
        if (existingOutlay.isEmpty()) {
            return new ResponseEntity<>("指定されたIDの出費データは存在しません。", 
            		HttpStatus.NOT_FOUND); // 404 Not Found を返す
        }
		return new ResponseEntity<>(existingOutlay.get(), HttpStatus.OK);
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<?> 
	updateOutlay(@PathVariable("id") final Integer id, 
				 @RequestBody final Map<String, Object> requestBody) {
		final Optional<Outlay> existingOutlay = outlayRepository.findById(id); // IDで既存の Outlay を検索
        if (existingOutlay.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404 Not Found を返す
        }

        try {
            final String item = (String) requestBody.get("item");
            final Integer amount = (Integer) requestBody.get("amount");
            final Outlay validatedOutlay = Outlay.create(item, amount);
            final Outlay outlayToUpdate = existingOutlay.get(); // Optional から Outlay オブジェクトを取得
            
            outlayToUpdate.setOutlayData(validatedOutlay.getOutlayData()); 
            outlayRepository.save(outlayToUpdate); 
            
            return new ResponseEntity<>(outlayToUpdate, HttpStatus.OK); // 200 OK と更新後の Outlay オブジェクトを返す
            
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST); // 400 Bad Request とエラーメッセージを返す
        }
        
	}
}
