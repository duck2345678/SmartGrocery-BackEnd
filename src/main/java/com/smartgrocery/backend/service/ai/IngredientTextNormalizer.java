package com.smartgrocery.backend.service.ai;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class IngredientTextNormalizer {

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
