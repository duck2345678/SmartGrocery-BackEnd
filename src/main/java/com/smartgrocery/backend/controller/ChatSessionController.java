package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.*;
import com.smartgrocery.backend.entity.ChatSession;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.service.ChatHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat/sessions")
@Tag(name = "AI Chat History", description = "API quản lý lịch sử trò chuyện AI")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatHistoryService chatHistoryService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Lấy danh sách các cuộc hội thoại của user (phân trang, mới nhất trước)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ChatSessionResponseDto>>> getSessions(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<ChatSession> sessions = chatHistoryService.getActiveSessions(user, pageable);
        
        Page<ChatSessionResponseDto> dtos = sessions.map(s -> ChatSessionResponseDto.builder()
                .id(s.getId())
                .title(s.getTitle())
                .contextType(s.getContextType())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build());
                
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(summary = "Tạo một cuộc hội thoại mới")
    @PostMapping
    public ResponseEntity<ApiResponse<ChatSessionResponseDto>> createSession(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) ChatSessionRequestDto request
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        String title = request != null ? request.getTitle() : "Cuộc trò chuyện mới";
        String contextType = request != null ? request.getContextType() : "GENERIC";
        
        ChatSession session = chatHistoryService.createSession(user, title, contextType);
        ChatSessionResponseDto dto = ChatSessionResponseDto.builder()
                .id(session.getId())
                .title(session.getTitle())
                .contextType(session.getContextType())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
                
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Lấy chi tiết cuộc hội thoại kèm danh sách tin nhắn")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatSessionDetailResponseDto>> getSessionDetails(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        ChatSession session = chatHistoryService.getSessionDetails(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc trò chuyện hoặc không có quyền truy cập"));

        List<ChatMessageResponseDto> messageDtos = session.getMessages().stream().map(m -> ChatMessageResponseDto.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .shoppingItems(parseShoppingItems(m.getShoppingItemsJson()))
                .latencyMs(m.getLatencyMs())
                .createdAt(m.getCreatedAt())
                .build()).collect(Collectors.toList());

        ChatSessionDetailResponseDto dto = ChatSessionDetailResponseDto.builder()
                .id(session.getId())
                .title(session.getTitle())
                .contextType(session.getContextType())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messages(messageDtos)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Đổi tên cuộc hội thoại")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ChatSessionResponseDto>> renameSession(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id,
            @RequestBody ChatSessionRequestDto request
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Tiêu đề không được để trống"));
        }
        
        ChatSession session = chatHistoryService.renameSession(id, user, request.getTitle());
        ChatSessionResponseDto dto = ChatSessionResponseDto.builder()
                .id(session.getId())
                .title(session.getTitle())
                .contextType(session.getContextType())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
                
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(summary = "Xóa cuộc hội thoại (soft delete)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        chatHistoryService.softDeleteSession(id, user);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private List<ChatResponseDto.ShoppingItem> parseShoppingItems(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, ChatResponseDto.ShoppingItem.class));
        } catch (Exception e) {
            log.error("Failed to parse shopping items from JSON", e);
            return null;
        }
    }
}
