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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(OutlayController.class)
// OutlayController の REST エンドポイントを MockMvc で網羅的に検証するテストクラス。
public class OutlayControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OutlayRepository outlayRepository;

  @Autowired private ObjectMapper objectMapper; // JSON変換用

  private Outlay outlay1;
  private Outlay outlay2;

  @BeforeEach
  // テスト間で再利用する標準データを生成し、ID と作成日時を固定する。
  void setUp() {
    outlay1 = Outlay.create("Lunch", 1000);
    outlay1.setId(1);
    outlay1.setCreatedAt(LocalDateTime.now());

    outlay2 = Outlay.create("Dinner", 2000);
    outlay2.setId(2);
    outlay2.setCreatedAt(LocalDateTime.now().minusDays(1));
  }

  @Test
  // 正常系：出費が存在する場合に全件取得 API が 200 と内容を返す。
  void getAllOutlays_shouldReturnAllOutlays() throws Exception {
    // Given
    List<Outlay> allOutlays = Arrays.asList(outlay1, outlay2);
    when(outlayRepository.findAll(Sort.unsorted())).thenReturn(allOutlays);

    // When
    ResultActions result = mockMvc.perform(get("/api/v1/outlays"));

    // Then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(outlay1.getId()))
        .andExpect(jsonPath("$[0].item").value(outlay1.getItem()))
        .andExpect(jsonPath("$[0].amount").value(outlay1.getAmount()))
        .andExpect(jsonPath("$[1].id").value(outlay2.getId()))
        .andExpect(jsonPath("$[1].item").value(outlay2.getItem()))
        .andExpect(jsonPath("$[1].amount").value(outlay2.getAmount()));
  }

  @Test
  // 異常系：データが空の場合に 404 とエラーメッセージを返す。
  void getAllOutlays_shouldReturnNotFound_whenNoOutlaysExist() throws Exception {
    // Given
    when(outlayRepository.findAll(Sort.unsorted())).thenReturn(Collections.emptyList());

    // When
    ResultActions result = mockMvc.perform(get("/api/v1/outlays"));

    // Then
    result.andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("出費データが存在しません"));
  }

  @Test
  // 正常系：item 昇順指定でソートされた結果が返る。
  void getAllOutlays_shouldReturnSortedOutlays_byItemAsc() throws Exception {
    // Given
    List<Outlay> sortedOutlays = Arrays.asList(outlay2, outlay1); // Dinner, Lunch
    when(outlayRepository.findAll(Sort.by(Sort.Direction.ASC, "item"))).thenReturn(sortedOutlays);

    // When
    ResultActions result = mockMvc.perform(get("/api/v1/outlays?sortBy=item&sortDirection=asc"));

    // Then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].item").value(outlay2.getItem()))
        .andExpect(jsonPath("$[1].item").value(outlay1.getItem()));
  }

  @Test
  // 正常系：amount 降順指定でソートされた結果が返る。
  void getAllOutlays_shouldReturnSortedOutlays_byAmountDesc() throws Exception {
    // Given
    List<Outlay> sortedOutlays = Arrays.asList(outlay2, outlay1); // 2000, 1000
    when(outlayRepository.findAll(Sort.by(Sort.Direction.DESC, "amount")))
        .thenReturn(sortedOutlays);

    // When
    ResultActions result = mockMvc.perform(get("/api/v1/outlays?sortBy=amount&sortDirection=desc"));

    // Then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].amount").value(outlay2.getAmount()))
        .andExpect(jsonPath("$[1].amount").value(outlay1.getAmount()));
  }

  @Test
  // 正常系：登録 API が 201 と作成した出費情報を返す。
  void registerOutlay_shouldCreateNewOutlay() throws Exception {
    // Given
    Outlay newOutlay = Outlay.create("Coffee", 500);
    newOutlay.setId(3);
    newOutlay.setCreatedAt(LocalDateTime.now());
    when(outlayRepository.save(any(Outlay.class))).thenReturn(newOutlay);
    String outlayRequestJson = "{\"item\": \"Coffee\", \"amount\": 500}";

    // When
    ResultActions result =
        mockMvc.perform(
            post("/api/v1/outlays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(outlayRequestJson));

    // Then
    result.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(newOutlay.getId()));
  }

  @Test
  // 異常系：登録時のバリデーション違反で 400 とエラー詳細を返す。
  void registerOutlay_shouldReturnBadRequest_whenValidationFails() throws Exception {
    // Given
    String invalidOutlayRequestJson = "{\"item\": \"\", \"amount\": 500}";

    // When
    ResultActions result =
        mockMvc.perform(
            post("/api/v1/outlays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidOutlayRequestJson));

    // Then
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.item").exists());
  }

  @Test
  // 正常系：ID 検索で出費を発見した場合に 200 と詳細を返す。
  void getOutlayById_shouldReturnOutlay_whenFound() throws Exception {
    // Given
    when(outlayRepository.findById(1)).thenReturn(Optional.of(outlay1));

    // When
    ResultActions result = mockMvc.perform(get("/api/v1/outlays/{id}", 1));

    // Then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(outlay1.getId()))
        .andExpect(jsonPath("$.item").value(outlay1.getItem()));
  }

  @Test
  // 異常系：ID 検索で未発見の場合に 404 とメッセージを返す。
  void getOutlayById_shouldReturnNotFound_whenNotFound() throws Exception {
    // Given
    when(outlayRepository.findById(99)).thenReturn(Optional.empty());

    // When
    ResultActions result = mockMvc.perform(get("/api/v1/outlays/{id}", 99));

    // Then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("指定されたIDの出費データは存在しません。"));
  }

  @Test
  // 正常系：指定日に出費が存在する場合に 200 と一覧を返す。
  void getOutlaysByDate_shouldReturnOutlays_whenFound() throws Exception {
    // Given
    LocalDate testDate = LocalDate.now();
    Outlay outlayOnDate = Outlay.create("Cafe", 700);
    outlayOnDate.setId(3);
    outlayOnDate.setCreatedAt(testDate.atStartOfDay());
    when(outlayRepository.findByCreatedAtDate(testDate)).thenReturn(Arrays.asList(outlayOnDate));

    // When
    ResultActions result =
        mockMvc.perform(get("/api/v1/outlays/on-date").param("date", testDate.toString()));

    // Then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].item").value(outlayOnDate.getItem()));
  }

  @Test
  // 異常系：指定日に出費が無い場合に 404 とメッセージを返す。
  void getOutlaysByDate_shouldReturnNotFound_whenNoOutlaysOnDate() throws Exception {
    // Given
    LocalDate testDate = LocalDate.of(2023, 1, 1);
    when(outlayRepository.findByCreatedAtDate(testDate)).thenReturn(Collections.emptyList());

    // When
    ResultActions result =
        mockMvc.perform(get("/api/v1/outlays/on-date").param("date", testDate.toString()));

    // Then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("指定された日付の出費データは存在しません。"));
  }

  @Test
  // 異常系：日付パラメータの形式が不正な場合に 400 を返す。
  void getOutlaysByDate_shouldReturnBadRequest_whenInvalidDateFormat() throws Exception {
    // Given
    String invalidDate = "invalid-date";

    // When
    ResultActions result =
        mockMvc.perform(get("/api/v1/outlays/on-date").param("date", invalidDate));

    // Then
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("日付の形式が正しくありません。YYYY-MM-DD の形式で入力してください。"));
  }

  @Test
  // 正常系：既存データを更新すると 200 と更新内容を返す。
  void updateOutlay_shouldUpdateExistingOutlay() throws Exception {
    // Given
    Outlay updatedOutlay = Outlay.create("Updated Lunch", 1500);
    updatedOutlay.setId(1);
    updatedOutlay.setCreatedAt(outlay1.getCreatedAt());
    when(outlayRepository.findById(1)).thenReturn(Optional.of(outlay1));
    when(outlayRepository.save(any(Outlay.class))).thenReturn(updatedOutlay);
    String updateRequestJson = "{\"item\": \"Updated Lunch\", \"amount\": 1500}";

    // When
    ResultActions result =
        mockMvc.perform(
            put("/api/v1/outlays/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson));

    // Then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.item").value(updatedOutlay.getItem()))
        .andExpect(jsonPath("$.amount").value(updatedOutlay.getAmount()));
  }

  @Test
  // 異常系：更新対象が存在しない場合に 404 とメッセージを返す。
  void updateOutlay_shouldReturnNotFound_whenOutlayDoesNotExist() throws Exception {
    // Given
    when(outlayRepository.findById(99)).thenReturn(Optional.empty());
    String updateRequestJson = "{\"item\": \"NonExistent\", \"amount\": 100}";

    // When
    ResultActions result =
        mockMvc.perform(
            put("/api/v1/outlays/{id}", 99)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestJson));

    // Then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("指定されたIDの出費データは存在しません。"));
  }

  @Test
  // 異常系：更新リクエストがバリデーションに失敗した場合に 400 を返す。
  void updateOutlay_shouldReturnBadRequest_whenValidationFails() throws Exception {
    // Given
    when(outlayRepository.findById(1)).thenReturn(Optional.of(outlay1));
    String invalidUpdateRequestJson = "{\"item\": \"\", \"amount\": 100}";

    // When
    ResultActions result =
        mockMvc.perform(
            put("/api/v1/outlays/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidUpdateRequestJson));

    // Then
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.item").exists());
  }

  @Test
  // 正常系：削除対象が存在する場合に 204 を返し、リポジトリから削除される。
  void deleteOutlay_shouldDeleteOutlay_whenExists() throws Exception {
    // Given
    when(outlayRepository.existsById(1)).thenReturn(true);
    doNothing().when(outlayRepository).deleteById(1);

    // When
    ResultActions result = mockMvc.perform(delete("/api/v1/outlays/{id}", 1));

    // Then
    result.andExpect(status().isNoContent());
    verify(outlayRepository, times(1)).deleteById(1);
  }

  @Test
  // 異常系：削除対象が存在しない場合に 404 とメッセージを返す。
  void deleteOutlay_shouldReturnNotFound_whenOutlayDoesNotExist() throws Exception {
    // Given
    when(outlayRepository.existsById(99)).thenReturn(false);

    // When
    ResultActions result = mockMvc.perform(delete("/api/v1/outlays/{id}", 99));

    // Then
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("指定されたIDの出費データは存在しません。"));
  }
}
