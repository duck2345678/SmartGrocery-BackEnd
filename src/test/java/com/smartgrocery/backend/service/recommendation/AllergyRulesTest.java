package com.smartgrocery.backend.service.recommendation;

import com.smartgrocery.backend.config.AllergyProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllergyRulesTest {

    @Test
    void seafoodAliasMatchesClamIngredient() {
        AllergyRules rules = newRules();
        Set<String> tokens = rules.extractTokens("Hải sản");

        assertTrue(rules.matchesAny("Nghêu sạch", tokens));
    }

    @Test
    void dairyAliasMatchesCheeseWithoutAccent() {
        AllergyRules rules = newRules();
        Set<String> tokens = rules.extractTokens("sữa");

        assertTrue(rules.matchesAny("pho mai bao soi", tokens));
    }

    @Test
    void peanutAndSesameAliasesWork() {
        AllergyRules rules = newRules();
        assertTrue(rules.matchesAny("banh trang me", rules.extractTokens("mè")));
        assertTrue(rules.matchesAny("sot dau phong", rules.extractTokens("đậu phộng")));
    }

    @Test
    void unrelatedIngredientDoesNotMatch() {
        AllergyRules rules = newRules();
        Set<String> tokens = rules.extractTokens("Hải sản");

        assertFalse(rules.matchesAny("Thăn bò Úc", tokens));
    }

    private AllergyRules newRules() {
        AllergyProperties props = new AllergyProperties();
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("hai-san", List.of("hai san", "seafood", "tom", "cua", "muc", "ngheu", "oc", "ca", "fish", "shrimp"));
        aliases.put("sua", List.of("sua", "pho mai", "phomai", "cheese", "butter", "bo", "kem", "yogurt", "sua tuoi"));
        aliases.put("trung", List.of("trung", "egg", "long do", "long trang"));
        aliases.put("dau-phong", List.of("dau phong", "peanut", "peanuts", "hat dieu", "hanh nhan", "oc cho", "me"));
        aliases.put("gluten", List.of("gluten", "bot mi", "mi", "banh mi", "my", "wheat", "lua mi"));
        props.setAliases(aliases);
        return new AllergyRules(props);
    }
}
