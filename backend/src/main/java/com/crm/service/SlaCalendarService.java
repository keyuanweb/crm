package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.sla.SlaCalendarConfigRequest;
import com.crm.dto.sla.SlaCalendarConfigResponse;
import com.crm.entity.SlaCalendarConfig;
import com.crm.repository.SlaCalendarConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SLA 工作日历服务（054，FR-L01~L08）：配置 CRUD + 工作时间推进计算。 */
@Service
public class SlaCalendarService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SlaCalendarConfigMapper configMapper;

  public SlaCalendarService(SlaCalendarConfigMapper configMapper) {
    this.configMapper = configMapper;
  }

  // ===== 配置 =====

  public SlaCalendarConfigResponse get() {
    SlaCalendarConfig config = load();
    return config == null ? null : toResponse(config);
  }

  @Transactional
  public SlaCalendarConfigResponse update(SlaCalendarConfigRequest req) {
    validate(req);
    SlaCalendarConfig config = load();
    if (config == null) {
      config = new SlaCalendarConfig();
    }
    config.setWorkSlots(writeJson(req.getWorkSlots()));
    config.setWorkDays(writeJson(req.getWorkDays()));
    config.setHolidays(writeJson(req.getHolidays()));
    config.setEnabled(req.getEnabled() ? 1 : 0);
    if (config.getId() == null) {
      configMapper.insert(config);
    } else {
      configMapper.updateById(config);
    }
    return toResponse(configMapper.selectById(config.getId()));
  }

  private void validate(SlaCalendarConfigRequest req) {
    if (req.getWorkSlots() != null) {
      for (SlaCalendarConfigRequest.WorkSlot slot : req.getWorkSlots()) {
        LocalTime start = parseTime(slot.getStart());
        LocalTime end = parseTime(slot.getEnd());
        if (start == null || end == null || !start.isBefore(end)) {
          throw new BusinessException(ErrorCode.SLA_CALENDAR_SLOT_INVALID);
        }
      }
    }
    if (req.getWorkDays() != null) {
      for (Integer day : req.getWorkDays()) {
        if (day == null || day < 1 || day > 7) {
          throw new BusinessException(ErrorCode.SLA_CALENDAR_DAY_INVALID);
        }
      }
    }
    if (req.getHolidays() != null) {
      for (String h : req.getHolidays()) {
        try {
          LocalDate.parse(h);
        } catch (Exception ex) {
          throw new BusinessException(ErrorCode.SLA_CALENDAR_HOLIDAY_INVALID);
        }
      }
    }
  }

  private LocalTime parseTime(String s) {
    try {
      return LocalTime.parse(s);
    } catch (Exception ex) {
      return null;
    }
  }

  private SlaCalendarConfig load() {
    return configMapper.selectOne(
        new LambdaQueryWrapper<SlaCalendarConfig>()
            .orderByAsc(SlaCalendarConfig::getId)
            .last("LIMIT 1"));
  }

  private String writeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
  }

  private SlaCalendarConfigResponse toResponse(SlaCalendarConfig config) {
    SlaCalendarConfigResponse resp = new SlaCalendarConfigResponse();
    resp.setId(config.getId());
    resp.setWorkSlots(readList(config.getWorkSlots(), new TypeReference<>() {}));
    resp.setWorkDays(readList(config.getWorkDays(), new TypeReference<>() {}));
    resp.setHolidays(readList(config.getHolidays(), new TypeReference<>() {}));
    resp.setEnabled(config.getEnabled() != null && config.getEnabled() == 1);
    resp.setUpdatedAt(config.getUpdatedAt());
    return resp;
  }

  private <T> List<T> readList(String json, TypeReference<List<T>> type) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(json, type);
    } catch (Exception ex) {
      return List.of();
    }
  }

  // ===== 工作时间推进（纯函数） =====

  /** 按工作日历推进 SLA 时长：仅在 工作时段 ∩ 工作日 ∩ 非节假日 内消耗。 未启用配置 → 直接 plusHours（回退旧行为）。 */
  public LocalDateTime advanceWorkingTime(LocalDateTime from, double hours) {
    SlaCalendarConfig config = load();
    if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
      return from.plusMinutes(Math.round(hours * 60));
    }
    List<SlaCalendarConfigRequest.WorkSlot> slots =
        readList(config.getWorkSlots(), new TypeReference<>() {});
    Set<Integer> workDays =
        readList(config.getWorkDays(), new TypeReference<List<Integer>>() {}).stream()
            .collect(Collectors.toSet());
    Set<LocalDate> holidays =
        readList(config.getHolidays(), new TypeReference<List<String>>() {}).stream()
            .map(LocalDate::parse)
            .collect(Collectors.toSet());
    if (slots.isEmpty()) {
      return from.plusMinutes(Math.round(hours * 60));
    }

    long remainingMinutes = Math.round(hours * 60);
    LocalDateTime cursor = from;

    while (remainingMinutes > 0) {
      // 当前日期是否工作日
      int day = cursor.getDayOfWeek().getValue(); // 1=周一
      boolean isWorkDay = workDays.contains(day) && !holidays.contains(cursor.toLocalDate());
      if (!isWorkDay) {
        // 跳到次日 00:00
        cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        continue;
      }
      // 当前时间落在哪个工作时段
      LocalTime time = cursor.toLocalTime();
      SlaCalendarConfigRequest.WorkSlot activeSlot = null;
      for (SlaCalendarConfigRequest.WorkSlot slot : slots) {
        LocalTime start = LocalTime.parse(slot.getStart());
        LocalTime end = LocalTime.parse(slot.getEnd());
        if (!time.isBefore(start) && time.isBefore(end)) {
          activeSlot = slot;
          break;
        }
      }
      if (activeSlot == null) {
        // 非工作时段 → 跳到下一时段开始或次日
        LocalTime nextStart = nextSlotStart(cursor, slots);
        if (nextStart != null && !nextStart.isBefore(time)) {
          cursor = cursor.toLocalDate().atTime(nextStart);
          continue;
        }
        cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        continue;
      }
      LocalTime start = LocalTime.parse(activeSlot.getStart());
      LocalTime end = LocalTime.parse(activeSlot.getEnd());
      long availableMinutes = java.time.Duration.between(time, end).toMinutes();
      if (availableMinutes <= 0) {
        cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        continue;
      }
      if (remainingMinutes <= availableMinutes) {
        cursor = cursor.plusMinutes(remainingMinutes);
        remainingMinutes = 0;
      } else {
        remainingMinutes -= availableMinutes;
        cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
      }
    }
    return cursor;
  }

  private LocalTime nextSlotStart(
      LocalDateTime cursor, List<SlaCalendarConfigRequest.WorkSlot> slots) {
    LocalTime time = cursor.toLocalTime();
    LocalTime earliest = null;
    for (SlaCalendarConfigRequest.WorkSlot slot : slots) {
      LocalTime start = LocalTime.parse(slot.getStart());
      if (start.isAfter(time) && (earliest == null || start.isBefore(earliest))) {
        earliest = start;
      }
    }
    return earliest;
  }
}
