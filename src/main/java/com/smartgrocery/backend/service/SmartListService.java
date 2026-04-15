package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.SmartList;
import com.smartgrocery.backend.entity.SmartListItem;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.SmartListItemRepository;
import com.smartgrocery.backend.repository.SmartListRepository;
import com.smartgrocery.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional("transactionManager")
public class SmartListService {

    @Autowired
    private SmartListRepository smartListRepository;

    @Autowired
    private SmartListItemRepository smartListItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    public SmartList createSmartList(Long userId, String name, String type, String note) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SmartList list = SmartList.builder()
                .user(user)
                .name(name)
                .type(type)
                .note(note)
                .build();

        return smartListRepository.save(list);
    }

    public SmartListItem addItemToList(Long listId, Long variantId, Integer quantity, String note) {
        SmartList list = smartListRepository.findById(listId)
                .orElseThrow(() -> new RuntimeException("Smart List not found"));

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        SmartListItem item = SmartListItem.builder()
                .smartList(list)
                .variant(variant)
                .quantity(quantity)
                .note(note)
                .build();

        return smartListItemRepository.save(item);
    }

    public List<SmartList> getUserLists(Long userId) {
        return smartListRepository.findByUser_Id(userId);
    }

    public List<SmartListItem> getListItems(Long listId) {
        return smartListItemRepository.findBySmartListId(listId);
    }

    public void deleteList(Long listId) {
        smartListRepository.deleteById(listId);
    }

    public void removeItemFromList(Long itemId) {
        smartListItemRepository.deleteById(itemId);
    }
}
