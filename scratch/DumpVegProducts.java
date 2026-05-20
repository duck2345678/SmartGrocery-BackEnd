import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class DumpVegProducts {
  static Map<String,String> env(Path p) throws Exception {
    Map<String,String> m=new HashMap<>();
    for(String r:Files.readAllLines(p)){
      String s=r.trim(); if(s.isEmpty()||s.startsWith("#")) continue;
      int i=s.indexOf('='); if(i<=0) continue;
      String k=s.substring(0,i).trim(); String v=s.substring(i+1).trim();
      if((v.startsWith("\"")&&v.endsWith("\""))||(v.startsWith("'")&&v.endsWith("'"))) v=v.substring(1,v.length()-1);
      m.put(k,v);
    }
    return m;
  }
  public static void main(String[] a) throws Exception {
    Map<String,String> e=env(Paths.get(".env"));
    Class.forName("org.postgresql.Driver");
    try(Connection c=DriverManager.getConnection(e.get("SUPABASE_DB_URL"),e.get("SUPABASE_DB_USERNAME"),e.get("SUPABASE_DB_PASSWORD"))){
      System.out.println("-- categories --");
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery("select category_id, category_name from categories order by category_id")){
        while(rs.next()) System.out.println(rs.getLong(1)+" | "+rs.getString(2));
      }
      System.out.println("-- products in rau cu like categories --");
      String sql="""
      select p.product_id,p.product_name,p.status,c.category_name
      from products p join categories c on c.category_id=p.category_id
      where lower(c.category_name) like lower('%rau%')
         or lower(c.category_name) like lower('%cu%')
         or lower(c.category_name) like lower('%thuc pham tuoi%')
      order by p.product_id
      limit 400
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)){
        while(rs.next()) System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | "+rs.getString(3)+" | "+rs.getString(4));
      }
    }
  }
}
