import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class FindCaiProducts {
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
      String sql="""
      select p.product_id,p.product_name,p.status,c.category_name
      from products p
      left join categories c on c.category_id=p.category_id
      where lower(p.product_name) like lower('%cai%')
      order by p.product_id
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)){
        while(rs.next()){
          System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | status="+rs.getString(3)+" | cat="+rs.getString(4));
        }
      }
    }
  }
}
