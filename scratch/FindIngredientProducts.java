import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class FindIngredientProducts {
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
      select p.product_id,p.product_name,p.status,
             coalesce(sum(coalesce(s.available_quantity,0)),0) as qty
      from products p
      left join product_variants v on v.product_id=p.product_id and upper(v.status)='ACTIVE'
      left join inventory_stocks s on s.variant_id=v.variant_id
      where lower(p.product_name) like lower('%ot%')
         or lower(p.product_name) like lower('%nghe%')
         or lower(p.product_name) like lower('%cai%')
         or lower(p.product_name) like lower('%cherry%')
      group by p.product_id,p.product_name,p.status
      order by p.product_id
      limit 200
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)){
        while(rs.next()){
          System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | status="+rs.getString(3)+" | qty="+rs.getLong(4));
        }
      }
    }
  }
}
