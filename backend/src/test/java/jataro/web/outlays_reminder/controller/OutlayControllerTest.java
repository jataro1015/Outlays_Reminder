package jataro.web.outlays_reminder.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jataro.web.outlays_reminder.entity.Outlay;
import jataro.web.outlays_reminder.repository.OutlayRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutlayController.class)
public class OutlayControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OutlayRepository outlayRepository;

  @Autowired private ObjectMapper objectMapper; // JSON変換用

  private Outlay outlay1;
  private Outlay outlay2;

  @BeforeEach
  void setUp() {
    // Outlay.create() を使用してインスタンスを生成し、その後セッターでIDと作成日時を設定
    outlay1 = Outlay.create("Lunch", 1000);
    outlay1.setId(1);
    outlay1.setCreatedAt(LocalDateTime.now());

    outlay2 = Outlay.create("Dinner", 2000);
    outlay2.setId(2);
    outlay2.setCreatedAt(LocalDateTime.now().minusDays(1));
  }

  @Test
  void getAllOutlays_shouldReturnAllOutlays() throws Exception {
    List<Outlay> allOutlays = Arrays.asList(outlay1, outlay2);
    when(outlayRepository.findAll(Sort.unsorted())).thenReturn(allOutlays);

    mockMvc
        .perform(get("/api/v1/outlays"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(outlay1.getId()))
        .andExpect(jsonPath("$[0].item").value(outlay1.getItem()))
        .andExpect(jsonPath("$[0].amount").value(outlay1.getAmount()))
        .andExpect(jsonPath("$[1].id").value(outlay2.getId()))
        .andExpect(jsonPath("$[1].item").value(outlay2.getItem()))
        .andExpect(jsonPath("$[1].amount").value(outlay2.getAmount()));
  }

  @Test
  void getAllOutlays_shouldReturnNotFound_whenNoOutlaysExist() throws Exception {
    when(outlayRepository.findAll(Sort.unsorted())).thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/api/v1/outlays"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("出費データが存在しません"));
  }

  @Test
  void getAllOutlays_shouldReturnSortedOutlays_byItemAsc() throws Exception {
    List<Outlay> sortedOutlays = Arrays.asList(outlay2, outlay1); // Dinner, Lunch
    when(outlayRepository.findAll(Sort.by(Sort.Direction.ASC, "item"))).thenReturn(sortedOutlays);

    mockMvc
        .perform(get("/api/v1/outlays?sortBy=item&sortDirection=asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].item").value(outlay2.getItem()))
        .andExpect(jsonPath("$[1].item").value(outlay1.getItem()));
  }

  @Test
  void getAllOutlays_shouldReturnSortedOutlays_byAmountDesc() throws Exception {
    List<Outlay> sortedOutlays = Arrays.asList(outlay2, outlay1); // 2000, 1000
    when(outlayRepository.findAll(Sort.by(Sort.Direction.DESC, "amount")))
        .thenReturn(sortedOutlays);

    mockMvc
        .perform(get("/api/v1/outlays?sortBy=amount&sortDirection=desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].amount").value(outlay2.getAmount()))
        .andExpect(jsonPath("$[1].amount").value(outlay1.getAmount()));
  }

  @Test
  void registerOutlay_shouldCreateNewOutlay() throws Exception {
    Outlay newOutlay = Outlay.create("Coffee", 500);
    newOutlay.setId(3);
    newOutlay.setCreatedAt(LocalDateTime.now());

    when(outlayRepository.save(any(Outlay.class))).thenReturn(newOutlay);

    String outlayRequestJson = "{\"item\": \"Coffee\", \"amount\": 500}";

    mockMvc
        .perform(
            post("/api/v1/outlays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(outlayRequestJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(newOutlay.getId()));
  }

  @Test
  void registerOutlay_shouldReturnBadRequest_whenValidationFails() throws Exception {
    String invalidOutlayRequestJson = "{\"item\": \"\", \"amount\": 500}"; // itemが空

    mockMvc
        .perform(
            post("/api/v1/outlays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidOutlayRequestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.item").exists()); // itemに関するエラーが存在すること
  }

  @Test
  void getOutlayById_shouldReturnOutlay_whenFound() throws Exception {
    when(outlayRepository.findById(1)).thenReturn(Optional.of(outlay1));

    mockMvc
        .perform(get("/api/v1/outlays/{id}", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(outlay1.getId()))
        .andExpect(jsonPath("$.item").value(outlay1.getItem()));
  }

  @Test
  void getOutlayById_shouldReturnNotFound_whenNotFound() throws Exception {
    when(outlayRepository.findById(99)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/outlays/{id}", 99))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("指定されたIDの出費データは存在しません。"));
  }

  @Test
  void getOutlaysByDate_shouldReturnOutlays_whenFound() throws Exception {
    LocalDate testDate = LocalDate.now();
    Outlay outlayOnDate = Outlay.create("Cafe", 700);
    outlayOnDate.setId(3);
    outlayOnDate.setCreatedAt(testDate.atStartOfDay());

    when(outlayRepository.findByCreatedAtDate(testDate)).thenReturn(Arrays.asList(outlayOnDate));

    mockMvc
        .perform(get("/api/v1/outlays/on-date").param("date", testDate.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].item").value(outlayOnDate.getItem()));
  }

  @Test
  void getOutlaysByDate_shouldReturnNotFound_whenNoOutlaysOnDate() throws Exception {
    LocalDate testDate = LocalDate.of(2023, 1, 1);
    when(outlayRepository.findByCreatedAtDate(testDate)).thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/api/v1/outlays/on-date").param("date", testDate.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("指定された日付の出費データは存在しません。"));
  }

  @Test
  void getOutlaysByDate_shouldReturnBadRequest_whenInvalidDateFormat() throws Exception {
    mockMvc
        .perform(get("/api/v1/outlays/on-date").param("date", "invalid-date"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("日付の形式が正しくありません。YYYY-MM-DD の形式で入力してください。"));
  }

  @Test
  void updateOutlay_shouldUpdateExistingOutlay() throws Exception {
    Outlay updatedOutlay = Outlay.create("Updated Lunch", 1500);
    updatedOutlay.setId(1);
    updatedOutlay.setCreatedAt(outlay1.getCreatedAt()); // 元の作成日時を保持

    when(outlayRepository.findById(1)).thenReturn(Optional.of(outlay1));
    when(outlayRepository.save(any(Outlay.class))).thenReturn(updatedOutlay);

    String updateRequestJson = "{\"item\": \"Updated Lunch\", \"amount\": 1500}";

    mockMvc
        .perform(
            put("/api/v1/outlays/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.item").value(updatedOutlay.getItem()))
        .andExpect(jsonPath("$.amount").value(updatedOutlay.getAmount()));
  }

  @Test
  void updateOutlay_shouldReturnNotFound_whenOutlayDoesNotExist() throws Exception {
    when(outlayRepository.findById(99)).thenReturn(Optional.empty());

    String updateRequestJson = "{\"item\": \"NonExistent\", \"amount\": 100}";

    mockMvc
        .perform(
            put("/api/v1/outlays/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("更新に失敗しました。指定されたIDの出費データは存在しません。"));
  }

  @Test
  void updateOutlay_shouldReturnBadRequest_whenValidationFails() throws Exception {
    when(outlayRepository.findById(1)).thenReturn(Optional.of(outlay1));

    String invalidUpdateRequestJson = "{\"item\": \"\", \"amount\": 100}"; // itemが空

    mockMvc
        .perform(
            put("/api/v1/outlays/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidUpdateRequestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.item").exists());
  }

  @Test
  void deleteOutlay_shouldDeleteOutlay_whenExists() throws Exception {
    when(outlayRepository.existsById(1)).thenReturn(true);
    doNothing().when(outlayRepository).deleteById(1);

    mockMvc.perform(delete("/api/v1/outlays/{id}", 1)).andExpect(status().isNoContent());

    verify(outlayRepository, times(1)).deleteById(1);
  }

  @Test
  void deleteOutlay_shouldReturnNotFound_whenOutlayDoesNotExist() throws Exception {
    when(outlayRepository.existsById(99)).thenReturn(false);

    mockMvc
        .perform(delete("/api/v1/outlays/{id}", 99))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("削除に失敗しました。指定されたIDの出費データは存在しません。"));
  }
}
