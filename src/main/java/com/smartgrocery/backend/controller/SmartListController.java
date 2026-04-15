package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.entity.SmartList;
import com.smartgrocery.backend.entity.SmartListItem;
import com.smartgrocery.backend.service.SmartListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/smart-lists")
@Tag(name = "Smart Shopping List", description = "API quản lý danh sách mua sắm thông minh")
public class SmartListController {

    @Autowired
    private SmartListService smartListService;

    @PostMapping("/{userId}")
    @Operation(summary = "Tạo danh sách mua sắm mới")
    public ResponseEntity<SmartList> createList(
            @PathVariable Long userId,
            @RequestParam String name,
            @RequestParam(defaultValue = "SHOPPING") String type,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(smartListService.createSmartList(userId, name, type, note));
    }

    @PostMapping("/{listId}/items")
    @Operation(summary = "Thêm sản phẩm vào danh sách")
    public ResponseEntity<SmartListItem> addItem(
            @PathVariable Long listId,
            @RequestParam Long variantId,
            @RequestParam Integer quantity,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(smartListService.addItemToList(listId, variantId, quantity, note));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy tất cả danh sách của người dùng")
    public ResponseEntity<List<SmartList>> getUserLists(@PathVariable Long userId) {
        return ResponseEntity.ok(smartListService.getUserLists(userId));
    }

    @GetMapping("/{listId}/items")
    @Operation(summary = "Lấy danh sách các sản phẩm trong list")
    public ResponseEntity<List<SmartListItem>> getListItems(@PathVariable Long listId) {
        return ResponseEntity.ok(smartListService.getListItems(listId));
    }

    @DeleteMapping("/{listId}")
    @Operation(summary = "Xóa danh sách")
    public ResponseEntity<Void> deleteList(@PathVariable Long listId) {
        smartListService.deleteList(listId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Xóa sản phẩm khỏi danh sách")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
        smartListService.removeItemFromList(itemId);
        return ResponseEntity.ok().build();
    }
}
