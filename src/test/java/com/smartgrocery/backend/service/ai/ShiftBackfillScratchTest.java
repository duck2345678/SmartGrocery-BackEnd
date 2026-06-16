package com.smartgrocery.backend.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Scratch test to backfill shift_schedules for all staff.
 * Inserts:
 *   - Tonight  (2026-06-15): shift_type = 'C' (Chiều/Tối 14:30-22:30)
 *   - Tomorrow (2026-06-16): shift_type = 'G' (Gãy) with selected_blocks = '1,2,3,4' (Sáng + Tối)
 *
 * Uses ON CONFLICT to skip if a schedule already exists for that user+date.
 * RUN ONCE then DELETE this file.
 */
@SpringBootTest
class ShiftBackfillScratchTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void backfillShifts() {
        // 1. Find all staff user IDs (users.user_id, roles.role_id, roles.role_name)
        List<Map<String, Object>> staffUsers = jdbcTemplate.queryForList(
                "SELECT u.user_id, u.full_name FROM users u JOIN roles r ON u.role_id = r.role_id WHERE UPPER(r.role_name) = 'STAFF'"
        );

        System.out.println("=== Found " + staffUsers.size() + " staff users ===");
        for (Map<String, Object> user : staffUsers) {
            System.out.println("  - ID=" + user.get("user_id") + "  Name=" + user.get("full_name"));
        }

        LocalDate tonight = LocalDate.of(2026, 6, 15);
        LocalDate tomorrow = LocalDate.of(2026, 6, 16);

        int insertedTonight = 0;
        int insertedTomorrow = 0;

        for (Map<String, Object> user : staffUsers) {
            Long userId = ((Number) user.get("user_id")).longValue();

            // Tonight: C shift (14:30 - 22:30)
            int r1 = jdbcTemplate.update(
                    "INSERT INTO shift_schedules (user_id, work_date, shift_type, selected_blocks, created_at, updated_at) " +
                    "VALUES (?, ?, 'C', NULL, NOW(), NOW()) " +
                    "ON CONFLICT (user_id, work_date) DO NOTHING",
                    userId, tonight
            );
            insertedTonight += r1;

            // Tomorrow: G shift (all blocks = Sáng + Tối)
            int r2 = jdbcTemplate.update(
                    "INSERT INTO shift_schedules (user_id, work_date, shift_type, selected_blocks, created_at, updated_at) " +
                    "VALUES (?, ?, 'G', '1,2,3,4', NOW(), NOW()) " +
                    "ON CONFLICT (user_id, work_date) DO NOTHING",
                    userId, tomorrow
            );
            insertedTomorrow += r2;
        }

        System.out.println("=== Backfill Results ===");
        System.out.println("  Tonight  (2026-06-15 C):     " + insertedTonight + " inserted");
        System.out.println("  Tomorrow (2026-06-16 G 1234): " + insertedTomorrow + " inserted");

        // Verify
        List<Map<String, Object>> verify = jdbcTemplate.queryForList(
                "SELECT ss.id, u.full_name, ss.work_date, ss.shift_type, ss.selected_blocks " +
                "FROM shift_schedules ss JOIN users u ON ss.user_id = u.user_id " +
                "WHERE ss.work_date IN (?, ?) ORDER BY ss.work_date, u.full_name",
                tonight, tomorrow
        );
        System.out.println("=== Verification (" + verify.size() + " rows) ===");
        for (Map<String, Object> row : verify) {
            System.out.printf("  ID=%-4s %-20s %s %s blocks=%s%n",
                    row.get("id"), row.get("full_name"), row.get("work_date"),
                    row.get("shift_type"), row.get("selected_blocks"));
        }
    }
}
