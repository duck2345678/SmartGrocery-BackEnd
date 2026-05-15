import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBDropAI {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:6543/postgres?pgbouncer=true&prepareThreshold=0";
        String user = "postgres.mrmqcbbqyeeyosgrrvna";
        String password = ".-FEY?w84Aayd%%";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
             
            System.out.println("Starting Backup...");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS backup_chat_sessions AS SELECT * FROM chat_sessions");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS backup_chat_messages AS SELECT * FROM chat_messages");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS backup_chat_message_feedbacks AS SELECT * FROM chat_message_feedbacks");
            System.out.println("Backup completed successfully.");

            System.out.println("Starting DROP...");
            stmt.executeUpdate("DROP TABLE IF EXISTS chat_message_feedbacks CASCADE");
            stmt.executeUpdate("DROP TABLE IF EXISTS chat_messages CASCADE");
            stmt.executeUpdate("DROP TABLE IF EXISTS chat_sessions CASCADE");
            System.out.println("DROP completed successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
