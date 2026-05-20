import java.sql.*;
import java.nio.file.*;
import java.util.*;

public class CatalogAudit {
  static Map<String,String> env() throws Exception {
    Map<String,String> m = new HashMap<>(System.getenv());
    Path p = Paths.get(".env");
    if (Files.exists(p)) {
      for (String line : Files.readAllLines(p)) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int i = line.indexOf('=');
        if (i > 0) m.putIfAbsent(line.substring(0,i), line.substring(i+1));
      }
    }
    return m;
  }
  static String s(ResultSet rs, String col) throws SQLException { String v = rs.getString(col); return v == null ? "" : v.replace("\t", " ").replace("\n", " "); }
  public static void main(String[] args) throws Exception {
    Map<String,String> e = env();
    try (Connection c = DriverManager.getConnection(e.get("SUPABASE_DB_URL"), e.get("SUPABASE_DB_USERNAME"), e.get("SUPABASE_DB_PASSWORD"))) {
      try (Statement st = c.createStatement()) {
        System.out.println("== CATEGORIES ==");
        try (ResultSet rs = st.executeQuery("select category_id, category_code, category_name, parent_category_id, is_active from categories order by category_id")) {
          while (rs.next()) System.out.printf("%s\t%s\t%s\t%s\t%s%n", rs.getLong(1), s(rs,"category_code"), s(rs,"category_name"), rs.getObject(4), rs.getBoolean(5));
        }
        System.out.println("== CATEGORY COUNTS ==");
        try (ResultSet rs = st.executeQuery("select c.category_id,c.category_code,c.category_name,count(p.product_id) cnt from categories c left join products p on p.category_id=c.category_id group by 1,2,3 order by cnt desc,c.category_id")) {
          while (rs.next()) System.out.printf("%s\t%s\t%s\t%s%n", rs.getLong(1), s(rs,"category_code"), s(rs,"category_name"), rs.getLong("cnt"));
        }
        System.out.println("== PRODUCTS ==");
        String sql = """
          select p.product_id,p.product_code,p.product_name,p.short_description,p.description,p.status,
                 c.category_id,c.category_code,c.category_name,
                 coalesce(string_agg(distinct pv.unit, ' | '), '') units,
                 coalesce(string_agg(distinct pv.package_size, ' | ') filter (where pv.package_size is not null and pv.package_size<>''), '') packages,
                 coalesce(string_agg(distinct pv.size, ' | ') filter (where pv.size is not null and pv.size<>''), '') sizes,
                 count(pv.variant_id) variant_count
          from products p
          left join categories c on c.category_id=p.category_id
          left join product_variants pv on pv.product_id=p.product_id
          group by p.product_id,p.product_code,p.product_name,p.short_description,p.description,p.status,c.category_id,c.category_code,c.category_name
          order by p.product_id
        """;
        try (ResultSet rs = st.executeQuery(sql)) {
          while (rs.next()) System.out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n",
            rs.getLong("product_id"), s(rs,"product_code"), s(rs,"product_name"), s(rs,"status"),
            rs.getLong("category_id"), s(rs,"category_code"), s(rs,"category_name"),
            s(rs,"units"), s(rs,"packages"), s(rs,"sizes"), rs.getLong("variant_count"));
        }
      }
    }
  }
}