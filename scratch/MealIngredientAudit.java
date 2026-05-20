import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class MealIngredientAudit {
    private static Map<String, String> loadEnv(Path envPath) throws Exception {
        Map<String, String> map = new HashMap<>();
        for (String raw : Files.readAllLines(envPath)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int idx = line.indexOf('=');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return map;
    }

    private static long queryLong(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void printTopIssuesForNewest174(Connection c) throws Exception {
        String sql = """
            WITH target_meals AS (
                SELECT meal_id
                FROM meals
                ORDER BY created_at DESC NULLS LAST, meal_id DESC
                LIMIT 174
            )
            SELECT m.meal_id, m.meal_name, COUNT(mi.meal_ingredient_id) AS ingredient_count
            FROM target_meals tm
            JOIN meals m ON m.meal_id = tm.meal_id
            LEFT JOIN meal_ingredients mi ON mi.meal_id = m.meal_id
            GROUP BY m.meal_id, m.meal_name
            HAVING COUNT(mi.meal_ingredient_id) = 0
            ORDER BY m.meal_id DESC
            LIMIT 20
        """;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            System.out.println("Meals in newest 174 with zero ingredients (up to 20):");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.println("- meal_id=" + rs.getLong("meal_id") + " | " + rs.getString("meal_name"));
            }
            if (!any) {
                System.out.println("- none");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            throw new IllegalStateException("Missing .env in working directory");
        }

        Map<String, String> env = loadEnv(envPath);
        String url = env.get("SUPABASE_DB_URL");
        String user = env.get("SUPABASE_DB_USERNAME");
        String pass = env.get("SUPABASE_DB_PASSWORD");

        if (url == null || user == null || pass == null) {
            throw new IllegalStateException("Missing SUPABASE_DB_URL/USERNAME/PASSWORD in .env");
        }

        Class.forName("org.postgresql.Driver");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            long totalMeals = queryLong(c, "SELECT COUNT(*) FROM meals");
            long totalMealIngredients = queryLong(c, "SELECT COUNT(*) FROM meal_ingredients");

            long mealsWithoutIngredients = queryLong(c, """
                SELECT COUNT(*)
                FROM (
                    SELECT m.meal_id
                    FROM meals m
                    LEFT JOIN meal_ingredients mi ON mi.meal_id = m.meal_id
                    GROUP BY m.meal_id
                    HAVING COUNT(mi.meal_ingredient_id) = 0
                ) x
            """);

            long orphanMealRefs = queryLong(c, """
                SELECT COUNT(*)
                FROM meal_ingredients mi
                LEFT JOIN meals m ON m.meal_id = mi.meal_id
                WHERE m.meal_id IS NULL
            """);

            long orphanProductRefs = queryLong(c, """
                SELECT COUNT(*)
                FROM meal_ingredients mi
                LEFT JOIN products p ON p.product_id = mi.product_id
                WHERE p.product_id IS NULL
            """);

            long newest174Count = queryLong(c, "SELECT COUNT(*) FROM (SELECT meal_id FROM meals ORDER BY created_at DESC NULLS LAST, meal_id DESC LIMIT 174) t");

            long newest174MealsWithoutIngredients = queryLong(c, """
                SELECT COUNT(*)
                FROM (
                    SELECT m.meal_id
                    FROM (SELECT meal_id FROM meals ORDER BY created_at DESC NULLS LAST, meal_id DESC LIMIT 174) tm
                    JOIN meals m ON m.meal_id = tm.meal_id
                    LEFT JOIN meal_ingredients mi ON mi.meal_id = m.meal_id
                    GROUP BY m.meal_id
                    HAVING COUNT(mi.meal_ingredient_id) = 0
                ) x
            """);

            long newest174OrphanProductRefs = queryLong(c, """
                SELECT COUNT(*)
                FROM meal_ingredients mi
                JOIN (SELECT meal_id FROM meals ORDER BY created_at DESC NULLS LAST, meal_id DESC LIMIT 174) tm
                  ON tm.meal_id = mi.meal_id
                LEFT JOIN products p ON p.product_id = mi.product_id
                WHERE p.product_id IS NULL
            """);

            System.out.println("=== Meal/Ingredient Integrity Audit ===");
            System.out.println("totalMeals=" + totalMeals);
            System.out.println("totalMealIngredients=" + totalMealIngredients);
            System.out.println("mealsWithoutIngredients=" + mealsWithoutIngredients);
            System.out.println("orphanMealRefsInMealIngredients=" + orphanMealRefs);
            System.out.println("orphanProductRefsInMealIngredients=" + orphanProductRefs);
            System.out.println("newest174MealsChecked=" + newest174Count);
            System.out.println("newest174MealsWithoutIngredients=" + newest174MealsWithoutIngredients);
            System.out.println("newest174OrphanProductRefs=" + newest174OrphanProductRefs);

            printTopIssuesForNewest174(c);
        }
    }
}
