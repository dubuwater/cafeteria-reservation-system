package kr.ac.cbnu.cafeteria.web.dto;

import java.util.List;

public record MenuSlotsResponse(MenuDto menu, List<SlotDto> slots) {
}
