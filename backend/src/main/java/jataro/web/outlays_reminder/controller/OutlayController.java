package jataro.web.outlays_reminder.controller;

import static jataro.web.outlays_reminder.constants.ErrorMessages.INVALID_DATE_FORMAT;
import static jataro.web.outlays_reminder.constants.ErrorMessages.INVALID_INPUT;
import static jataro.web.outlays_reminder.constants.ErrorMessages.INVALID_REQUEST_BODY_TYPE;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAYS_NOT_FOUND;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_CREATION_FAILED;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_DELETE_ERROR;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_FETCH_BY_DATE_ERROR;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_FETCH_BY_ID_ERROR;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_FETCH_ERROR;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_NOT_FOUND_BY_DATE;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_NOT_FOUND_BY_ID;
import static jataro.web.outlays_reminder.constants.ErrorMessages.OUTLAY_UPDATE_ERROR;
import static jataro.web.outlays_reminder.constants.ErrorMessages.REQUEST_PARAMETER_INVALID;

import jakarta.validation.Valid;
import jataro.web.outlays_reminder.dto.OutlayRequest;
import jataro.web.outlays_reminder.entity.Outlay;
import jataro.web.outlays_reminder.repository.OutlayRepository;
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

@RestController
@RequestMapping("/api/v1/outlays")
@CrossOrigin
public final class OutlayController {

  @Autowired private OutlayRepository outlayRepository;

  private static final Logger logger = LoggerFactory.getLogger(OutlayController.class);

  private OutlayController() {}

  @PostMapping
  public ResponseEntity<?> registerOutlay(
      @Valid @RequestBody final OutlayRequest request, final BindingResult result) {

    final var errors = handleValidationErrors(result);
    if (errors.isPresent()) {
      return errors.get();
    }

    try {
      final Outlay outlay = Outlay.create(request.getItem(), request.getAmount());
      final Outlay savedOutlay = outlayRepository.save(outlay);

      return ResponseEntity.created(
              ServletUriComponentsBuilder.fromCurrentRequestUri()
                  .path("/{id}")
                  .buildAndExpand(savedOutlay.getId())
                  .toUri())
          .body(Map.of("id", savedOutlay.getId()));

    } catch (IllegalArgumentException e) {
      return createErrorResponse(INVALID_INPUT, HttpStatus.BAD_REQUEST, e);

    } catch (ClassCastException e) {
      return createErrorResponse(INVALID_REQUEST_BODY_TYPE, HttpStatus.BAD_REQUEST, e);

    } catch (Exception e) {
      return createErrorResponse(OUTLAY_CREATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @GetMapping
  public ResponseEntity<?> getAllOutlays(
      @RequestParam(value = "sortBy", required = false) final String sortBy,
      @RequestParam(value = "sortDirection", required = false, defaultValue = "asc")
          final String sortDirection) {
    try {
      Sort sort = Sort.unsorted();
      if (sortBy != null) {
        final var escapedSortBy = HtmlUtils.htmlEscape(sortBy, StandardCharsets.UTF_8.name());
        final var escapedSortDirection =
            HtmlUtils.htmlEscape(sortDirection, StandardCharsets.UTF_8.name());
        final var direction =
            "desc".equalsIgnoreCase(escapedSortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        final Map<String, String> sortableFields =
            Map.of(
                "item", "item",
                "amount", "amount",
                "createdAt", "createdAt",
                "id", "id");

        if (sortableFields.containsKey(escapedSortBy.toLowerCase())) {
          sort = Sort.by(direction, sortableFields.get(escapedSortBy.toLowerCase()));
        }
      }

      final var outlays = outlayRepository.findAll(sort);

      return outlays.isEmpty()
          ? createErrorResponse(OUTLAYS_NOT_FOUND, HttpStatus.NOT_FOUND)
          : ResponseEntity.ok(outlays);

    } catch (Exception e) {
      return createErrorResponse(OUTLAY_FETCH_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getOutlayById(@PathVariable("id") final Integer id) {
    try {
      final Optional<Outlay> existingOutlay = outlayRepository.findById(id);

      return existingOutlay.isPresent()
          ? ResponseEntity.ok(existingOutlay.get())
          : createErrorResponse(OUTLAY_NOT_FOUND_BY_ID, HttpStatus.NOT_FOUND);

    } catch (Exception e) {
      return createErrorResponse(OUTLAY_FETCH_BY_ID_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @GetMapping("on-date")
  public ResponseEntity<?> getOutlaysByDate(
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
    try {
      final List<Outlay> outlays = outlayRepository.findByCreatedAtDate(date);

      return outlays.isEmpty()
          ? createErrorResponse(OUTLAY_NOT_FOUND_BY_DATE, HttpStatus.NOT_FOUND)
          : ResponseEntity.ok(outlays);
    } catch (Exception e) {
      return createErrorResponse(OUTLAY_FETCH_BY_DATE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateOutlay(
      @PathVariable("id") final Integer id,
      @Valid @RequestBody final OutlayRequest request,
      final BindingResult result) {

    final Optional<Outlay> existingOutlay = outlayRepository.findById(id);
    if (existingOutlay.isEmpty()) {
      return createErrorResponse(OUTLAY_NOT_FOUND_BY_ID, HttpStatus.NOT_FOUND);
    }

    final var errors = handleValidationErrors(result);
    if (errors.isPresent()) {
      return errors.get();
    }

    try {
      final var outlayToUpdate = existingOutlay.get();
      outlayToUpdate.setItem(request.getItem());
      outlayToUpdate.setAmount(request.getAmount());
      final Outlay updatedOutlay = outlayRepository.save(outlayToUpdate);

      return ResponseEntity.ok(updatedOutlay);

    } catch (Exception e) {
      return createErrorResponse(OUTLAY_UPDATE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteOutlay(@PathVariable("id") final Integer id) {
    try {
      if (!outlayRepository.existsById(id)) {
        return createErrorResponse(OUTLAY_NOT_FOUND_BY_ID, HttpStatus.NOT_FOUND);
      }

      outlayRepository.deleteById(id);

      return ResponseEntity.noContent().build();

    } catch (Exception e) {
      return createErrorResponse(OUTLAY_DELETE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<?> handleDateParseException(MethodArgumentTypeMismatchException ex) {
    if ("date".equals(ex.getName())) {
      return new ResponseEntity<>(Map.of("message", INVALID_DATE_FORMAT), HttpStatus.BAD_REQUEST);
    }
    return new ResponseEntity<>(
        Map.of("message", REQUEST_PARAMETER_INVALID), HttpStatus.BAD_REQUEST);
  }

  private Optional<ResponseEntity<?>> handleValidationErrors(final BindingResult result) {
    if (result.hasErrors()) {
      final Map<String, List<String>> errors =
          result.getFieldErrors().stream()
              .collect(
                  Collectors.groupingBy(
                      FieldError::getField,
                      Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
      return Optional.of(new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST));
    }
    return Optional.empty();
  }

  private ResponseEntity<?> createErrorResponse(final String message, final HttpStatus status) {
    final Map<String, String> responseBody = new HashMap<>();
    responseBody.put("message", message);
    return new ResponseEntity<>(responseBody, status);
  }

  private ResponseEntity<?> createErrorResponse(
      final String message, final HttpStatus status, final Exception e) {
    if (e instanceof IllegalArgumentException || e instanceof ClassCastException) {
      logger.warn(message, e);
    } else {
      logger.error(message, e);
    }
    return createErrorResponse(message, status);
  }
}
