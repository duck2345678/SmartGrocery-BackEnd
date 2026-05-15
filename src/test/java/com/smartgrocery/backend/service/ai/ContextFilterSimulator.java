package com.smartgrocery.backend.service.ai;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight simulator to exercise context filters (non-food and excluded ingredients)
 * without DB dependencies. Useful for quick checks of the five canonical prompts.
 */
public class ContextFilterSimulator {

    private static final Set<String> NON_FOOD_NAME_DENY = Set.of(
            "lau san", "lau bep", "xit lau", "giay bac", "pin", "nuoc tang luc", "ruou vang", "nuoc giat", "khan giay"
    );

    private static final Set<String> SEAFOOD_NAME_DENY = Set.of(
            "tom", "cua", "muc", "bach tuoc", "ca hoi", "ca ngu", "hai san", "shrimp", "crab", "fish"
    );

    record ProposedItem(long id, String name) {}

    static class Payload {
        List<ProposedItem> proposedItems = new ArrayList<>();
        String reply;
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isMealOrDietIntent(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains("bua toi") || n.contains("giam can") || n.contains("diet")
                || n.contains("protein") || n.contains("thuc don") || n.contains("an toi")
                || isShoppingListRequest(userMessage);
    }

    private static boolean isShoppingListRequest(String userMessage) {
        String n = normalizeText(userMessage);
        return n.contains("tao danh sach") || n.contains("danh sach mua") || n.contains("mua sam")
                || n.contains("tao danh sach mua") || n.contains("danh sach");
    }

    private static void filterNonFoodForMealIntent(Payload payload, String userMessage) {
        if (!isMealOrDietIntent(userMessage)) return;
        List<ProposedItem> before = new ArrayList<>(payload.proposedItems);
        payload.proposedItems = payload.proposedItems.stream().filter(item -> {
            String name = normalizeText(item.name);
            for (String deny : NON_FOOD_NAME_DENY) {
                if (name.contains(deny)) {
                    System.out.printf("Context Guard: rejected '%s' — matched non-food pattern '%s'\n", item.name, deny);
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
        if (payload.proposedItems.size() < before.size()) {
            System.out.printf("Context Guard: removed %d non-food items for meal/diet intent\n", before.size() - payload.proposedItems.size());
        }
    }

    private static void filterExcludedIngredients(Payload payload, String userMessage) {
        String n = normalizeText(userMessage);
        boolean excludeSeafood = n.contains("khong an hai san") || n.contains("khong an tom") || n.contains("khong an ca") || n.contains("di ung hai san") || n.contains("khong an hai san");
        if (!excludeSeafood) return;
        List<ProposedItem> before = new ArrayList<>(payload.proposedItems);
        payload.proposedItems = payload.proposedItems.stream().filter(item -> {
            String name = normalizeText(item.name);
            for (String deny : SEAFOOD_NAME_DENY) {
                if (name.contains(deny)) {
                    System.out.printf("Exclusion Guard: rejected '%s' — matched seafood pattern '%s'\n", item.name, deny);
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
        if (payload.proposedItems.size() < before.size()) {
            System.out.printf("Exclusion Guard: removed %d seafood items per user constraint\n", before.size() - payload.proposedItems.size());
        }
    }

    private static void ensureProposedItemsForShoppingAction(Payload payload, String userMessage) {
        if (payload.proposedItems != null && !payload.proposedItems.isEmpty()) return;
        if (!isShoppingListRequest(userMessage)) return;
        // No candidates → ask clarification
        payload.reply = "Bạn muốn mình tạo danh sách cho mục tiêu nào? Ví dụ: bữa tối giảm cân, bữa sáng healthy, hoặc danh sách giàu protein.";
        System.out.println("No candidates: asking for clarification.");
    }

    public static void main(String[] args) {
        List<Map.Entry<String, List<ProposedItem>>> scenarios = new ArrayList<>();

        // Case 1
        scenarios.add(Map.entry("Tạo danh sách mua sắm cho bữa tối giảm cân", List.of(
                new ProposedItem(1, "Chổi lau nhà"),
                new ProposedItem(2, "Xịt lau bếp"),
                new ProposedItem(3, "Giấy bạc"),
                new ProposedItem(4, "Pin AAA"),
                new ProposedItem(5, "Nước lau sàn"),
                new ProposedItem(6, "Rượu vang"),
                new ProposedItem(7, "Khoai lang"),
                new ProposedItem(8, "Ức gà"),
                new ProposedItem(9, "Sữa chua"),
                new ProposedItem(10, "Dầu ô liu")
        )));

        // Case 2
        scenarios.add(Map.entry("Tạo cho tôi list ăn tối nhẹ nhẹ, no lâu, đừng quá ngấy", List.of(
                new ProposedItem(11, "Măng tây"),
                new ProposedItem(12, "Khoai lang"),
                new ProposedItem(13, "Pin AAA"),
                new ProposedItem(14, "Yến mạch"),
                new ProposedItem(15, "Su hào")
        )));

        // Case 3
        scenarios.add(Map.entry("Tôi không ăn hải sản, tạo danh sách mua sắm giàu protein giúp tôi", List.of(
                new ProposedItem(16, "Tôm tươi"),
                new ProposedItem(17, "Cua"),
                new ProposedItem(18, "Cá hồi"),
                new ProposedItem(19, "Ức gà"),
                new ProposedItem(20, "Đậu phụ"),
                new ProposedItem(21, "Trứng gà")
        )));

        // Case 4
        scenarios.add(Map.entry("Ok tạo danh sách mua sắm đi", Collections.emptyList()));

        // Case 5
        scenarios.add(Map.entry("Tạo danh sách mua sắm dùm tôi", Collections.emptyList()));

        int i = 1;
        for (var sc : scenarios) {
            System.out.println("---\nCase " + i + ": " + sc.getKey());
            Payload p = new Payload();
            p.proposedItems.addAll(sc.getValue());

            // Apply meal/diet non-food filter
            filterNonFoodForMealIntent(p, sc.getKey());
            // Apply exclusion filter
            filterExcludedIngredients(p, sc.getKey());
            // Ensure shopping action clarifies when no candidates
            ensureProposedItemsForShoppingAction(p, sc.getKey());

            System.out.println("Result reply: " + (p.reply == null ? "(none)" : p.reply));
            System.out.println("Proposed items:");
            for (ProposedItem it : p.proposedItems) {
                System.out.println(" - " + it.name());
            }
            i++;
        }
    }
}
