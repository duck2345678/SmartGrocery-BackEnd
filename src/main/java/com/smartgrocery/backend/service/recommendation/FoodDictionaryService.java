package com.smartgrocery.backend.service.recommendation;

import com.smartgrocery.backend.dto.response.ProductDictionaryProjection;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodDictionaryService {

    private final ProductNodeRepository productNodeRepository;

    // Sử dụng AtomicReference để đảm bảo Thread-Safety khi Refresh Cache
    private final AtomicReference<DictionaryState> dictionaryStateRef = new AtomicReference<>();

    private static class DictionaryState {
        final Trie pass1Trie; // Match có dấu
        final Trie pass2Trie; // Match không dấu
        final Map<String, Long> pass1Mapping; // Keyword -> ProductId
        final Map<String, Long> pass2Mapping;

        DictionaryState(Trie pass1Trie, Trie pass2Trie, Map<String, Long> pass1Mapping, Map<String, Long> pass2Mapping) {
            this.pass1Trie = pass1Trie;
            this.pass2Trie = pass2Trie;
            this.pass1Mapping = pass1Mapping;
            this.pass2Mapping = pass2Mapping;
        }
    }

    @PostConstruct
    public void init() {
        refreshDictionary();
    }

    /**
     * Rebuild the dictionary from Neo4j completely in-memory, then atomic swap.
     */
    public void refreshDictionary() {
        try {
            log.info("Bắt đầu khởi tạo Food Dictionary từ Neo4j...");
            List<ProductDictionaryProjection> projections = productNodeRepository.findAllForDictionary();

            Trie.TrieBuilder pass1Builder = Trie.builder().ignoreOverlaps().onlyWholeWords().ignoreCase();
            Trie.TrieBuilder pass2Builder = Trie.builder().ignoreOverlaps().onlyWholeWords().ignoreCase();

            Map<String, Long> p1Map = new HashMap<>();
            Map<String, Long> p2Map = new HashMap<>();

            for (ProductDictionaryProjection p : projections) {
                if (p.getProductName() == null || p.getProductId() == null) continue;

                List<String> rawKeywords = new ArrayList<>();
                rawKeywords.add(p.getProductName());
                if (p.getSynonyms() != null) {
                    rawKeywords.addAll(p.getSynonyms());
                }

                for (String rawKeyword : rawKeywords) {
                    if (rawKeyword == null || rawKeyword.isBlank()) continue;

                    // Pass 1: Chuỗi gốc (Có thể fix teencode nhẹ)
                    String p1Keyword = normalizePass1(rawKeyword);
                    if (!p1Keyword.isBlank()) {
                        pass1Builder.addKeyword(p1Keyword);
                        p1Map.put(p1Keyword, p.getProductId());
                    }

                    // Pass 2: Không dấu hoàn toàn
                    String p2Keyword = normalizePass2(rawKeyword);
                    // Chỉ thêm vào Pass 2 nếu từ khóa >= 3 ký tự để tránh nhiễu ("bo" -> bò, bó, bơ...)
                    if (!p2Keyword.isBlank() && p2Keyword.length() >= 3) {
                        pass2Builder.addKeyword(p2Keyword);
                        p2Map.put(p2Keyword, p.getProductId());
                    }
                }
            }

            // Gán lại state bằng AtomicReference
            dictionaryStateRef.set(new DictionaryState(
                    pass1Builder.build(),
                    pass2Builder.build(),
                    p1Map,
                    p2Map
            ));

            log.info("Load thành công {} từ khóa (Pass 1) và {} từ khóa (Pass 2) vào Food Dictionary.",
                    p1Map.size(), p2Map.size());

        } catch (Exception e) {
            log.error("Lỗi khi load Food Dictionary từ Neo4j: {}", e.getMessage(), e);
        }
    }

    /**
     * Trích xuất Product IDs từ câu chat của User bằng cơ chế 2-Pass Aho-Corasick.
     */
    public List<Long> extractProductIds(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return List.of();

        DictionaryState state = dictionaryStateRef.get();
        if (state == null) return List.of();

        // Chuẩn hóa Pass 1 (Giữ dấu)
        String p1Msg = normalizePass1(userMessage);
        Collection<Emit> p1Emits = state.pass1Trie.parseText(p1Msg);
        
        if (!p1Emits.isEmpty()) {
            // Nếu Pass 1 (có dấu) match thành công -> Ưu tiên tuyệt đối
            return p1Emits.stream()
                    .map(Emit::getKeyword)
                    .map(state.pass1Mapping::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }

        // Nếu Pass 1 thất bại, thử Pass 2 (Không dấu)
        String p2Msg = normalizePass2(userMessage);
        Collection<Emit> p2Emits = state.pass2Trie.parseText(p2Msg);
        
        return p2Emits.stream()
                .map(Emit::getKeyword)
                .map(state.pass2Mapping::get)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizePass1(String text) {
        if (text == null) return "";
        // Replace teencode (giữ nguyên dấu)
        String s = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\bko\\b", "không")
                .replaceAll("\\bdc\\b", "được")
                .replaceAll("\\bvs\\b", "với")
                .replaceAll("\\btk\\b", "tài khoản")
                .replaceAll("\\bsp\\b", "sản phẩm");
        // Giữ lại chữ và số, bỏ ký tự đặc biệt
        return s.replaceAll("[^a-z0-9àáảãạâầấẩẫậăằắẳẵặèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Pattern DIACRITICS_AND_FRIENDS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private String normalizePass2(String text) {
        String p1 = normalizePass1(text);
        if (p1.isBlank()) return "";
        
        // Remove diacritics
        String nfdNormalizedString = Normalizer.normalize(p1, Normalizer.Form.NFD);
        String noAccent = DIACRITICS_AND_FRIENDS.matcher(nfdNormalizedString).replaceAll("");
        // Xử lý riêng chữ đ
        return noAccent.replaceAll("đ", "d").replaceAll("Đ", "D");
    }
}
