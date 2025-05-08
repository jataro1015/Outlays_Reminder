package jataro.web.outlays_reminder.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import jakarta.validation.Valid;
import jataro.web.outlays_reminder.dto.OutlayRequest;
import jataro.web.outlays_reminder.entity.Outlay;
import jataro.web.outlays_reminder.repository.OutlayRepository;

@RestController
@RequestMapping("/api/v1/outlays")
@CrossOrigin
public final class OutlayController {

	@Autowired
	private OutlayRepository outlayRepository;
	
	private static final Logger logger = LoggerFactory.getLogger(OutlayController.class);

	private OutlayController() {}

	@PostMapping
	public ResponseEntity<?> 
	registerOutlay(@Valid @RequestBody final OutlayRequest request, 
			final BindingResult result) {
		
		if(handleValidationErrors(result).isPresent()) {
			return handleValidationErrors(result).get();
		}
		
		try {
			final Outlay outlay = Outlay.create(request.getItem(), request.getAmount());
			final Outlay savedOutlay = outlayRepository.save(outlay);
			
			// ★ 201 Created ステータスと JSON レスポンスを返す
	        return ResponseEntity.created(
	                ServletUriComponentsBuilder.fromCurrentRequestUri() // 現在のリクエスト URI をベースに
	                        .path("/{id}") // ID をパスに追加
	                        .buildAndExpand(savedOutlay.getId()) // ID を展開して URI を作成
	                        .toUri()) // 作成されたリソースの URI を取得
	                		.body(Map.of("id", savedOutlay.getId())); // レスポンスボディに ID を JSON 形式で含める
	        
		} catch (IllegalArgumentException e) {
			return createErrorResponse("入力値が不正です。", 
					HttpStatus.BAD_REQUEST, e);
			
		}catch (ClassCastException e) {
			return createErrorResponse("リクエストボディの型を合わせてください。",
					HttpStatus.BAD_REQUEST, e);
			
		} catch (Exception e) {
			return createErrorResponse("出費データ登録中にエラーが発生しました。", 
					HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	@GetMapping
	public ResponseEntity<?> getAllOutlays(
			@RequestParam(value = "sortBy", required = false) final String sortBy,
			@RequestParam(value = "sortDirection", required = false, defaultValue = "asc") final String sortDirection) {
		try {
			Sort sort = Sort.unsorted();
			if(sortBy != null) {
				final var escapedSortBy = HtmlUtils.htmlEscape(sortBy, StandardCharsets.UTF_8.name());
				final var escapedSortDirection = HtmlUtils.htmlEscape(sortDirection, StandardCharsets.UTF_8.name());
				final var direction = "desc".equalsIgnoreCase(escapedSortDirection) 
						? Sort.Direction.DESC : Sort.Direction.ASC;
				
                final Map<String, String> sortableFields = Map.of(
                        "item", "item",
                        "amount", "amount",
                        "createdAt", "createdAt",
                        "id", "id"
                );

                if (sortableFields.containsKey(escapedSortBy.toLowerCase())) {
                    sort = Sort.by(direction, sortableFields.get(escapedSortBy.toLowerCase()));
                }
			}
			
			final var outlays = outlayRepository.findAll(sort);
			
			return outlays.isEmpty() 
					? createErrorResponse("出費データが存在しません", HttpStatus.NOT_FOUND) 
					: ResponseEntity.ok(outlays);

		} catch (Exception e) {
			return createErrorResponse("出費データ取得中にエラーが発生しました。", 
					HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getOutlayById(@PathVariable("id") final Integer id){
		try {
			final Optional<Outlay> existingOutlay = outlayRepository.findById(id); // IDで既存の Outlay を検索
			
	        return existingOutlay.isPresent()
	        		? ResponseEntity.ok(existingOutlay.get())
	        		: createErrorResponse("指定されたIDの出費データは存在しません。", HttpStatus.NOT_FOUND);
			
		} catch (Exception e) {
			return createErrorResponse("ID指定による出費データ取得中にエラーが発生しました。", 
					HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}
	
	@GetMapping("on-date")
	public ResponseEntity<?> getOutlaysByDate(@RequestParam("date") 
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date){
		try {
			final List<Outlay> outlays = outlayRepository.findByCreatedAtDate(date);
			
			return outlays.isEmpty()
					? new ResponseEntity<>(Map.of("message", "指定された日付の出費データは存在しません。"), HttpStatus.NOT_FOUND)
					: ResponseEntity.ok(outlays);
		} catch (Exception e) {
			return createErrorResponse("日付指定による出費データ取得中にエラーが発生しました。", 
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updateOutlay(@PathVariable("id") final Integer id, 
			@Valid @RequestBody final OutlayRequest request,
			final BindingResult result) {
		
		final Optional<Outlay> existingOutlay = outlayRepository.findById(id); // IDで既存の Outlay を検索
		
		if (existingOutlay.isEmpty()) {
			return createErrorResponse("更新に失敗しました。指定されたIDの出費データは存在しません。", 
					HttpStatus.NOT_FOUND);
		}
		
		if(handleValidationErrors(result).isPresent()) {
			return handleValidationErrors(result).get();
		}

		try {
			final var outlayToUpdate = existingOutlay.get();
			outlayToUpdate.setItem(request.getItem());
			outlayToUpdate.setAmount(request.getAmount());
			final Outlay updatedOutlay = outlayRepository.save(outlayToUpdate);

	        return ResponseEntity.ok(updatedOutlay);

		} catch (Exception e) {
			return createErrorResponse("出費データ更新中にサーバーエラーが発生しました。", // エラーメッセージをサーバーエラーに
					HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteOutlay(@PathVariable("id") final Integer id) {
		try {
			if(!outlayRepository.existsById(id)) {
				return createErrorResponse("削除に失敗しました。指定されたIDの出費データは存在しません。", 
						HttpStatus.NOT_FOUND);
			}

			outlayRepository.deleteById(id);

			return ResponseEntity.noContent().build();
			
		} catch (Exception e) {
			return createErrorResponse("ID指定による出費データ削除中にエラーが発生しました。", 
					HttpStatus.INTERNAL_SERVER_ERROR, e);
		}	
	}
	
	//アノテーションにmessage属性がない場合、これで例外を捕捉する。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleDateParseException(MethodArgumentTypeMismatchException ex) {
        if (ex.getName().equals("date")) {
            return new ResponseEntity<>(Map.of("message", "日付の形式が正しくありません。YYYY-MM-DD の形式で入力してください。"), HttpStatus.BAD_REQUEST);
        }
        // 他の型変換エラーが発生した場合の処理 (必要に応じて)
        return new ResponseEntity<>(Map.of("message", "リクエストパラメータの形式が不正です。"), HttpStatus.BAD_REQUEST);
    }
	
	private Optional<ResponseEntity<?>> handleValidationErrors(final BindingResult result) {
		if(result.hasErrors()) {
			final Map<String, List<String>> errors = result.getFieldErrors()
					.stream()
					.collect(Collectors.groupingBy(
		                    FieldError::getField,
		                    Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
			return Optional.ofNullable(new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST));
		}
		return Optional.empty();
	}

	private ResponseEntity<?> createErrorResponse(final String message, 
			final HttpStatus status) {
		final Map<String, String> responseBody = new HashMap<>();
		responseBody.put("message", message);
		return new ResponseEntity<>(responseBody, status);
	}

	private ResponseEntity<?> createErrorResponse(final String message, 
			final HttpStatus status, final Exception e) {
		if(e instanceof IllegalArgumentException 
				|| e instanceof ClassCastException) {
			logger.warn(message, e);

		} else {
			logger.error(message, e);
		}
		return createErrorResponse(message, status); // メッセージとステータスコードのみのオーバーロードを呼び出す
	}
}
