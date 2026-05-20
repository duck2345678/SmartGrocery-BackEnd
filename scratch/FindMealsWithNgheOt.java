import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class FindMealsWithNgheOt {
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
      select m.meal_id,m.meal_name,mi.generic_name,p.product_name,p.status
      from meal_ingredients mi
      join meals m on m.meal_id=mi.meal_id
      join products p on p.product_id=mi.product_id
      where lower(mi.generic_name) like lower('%ngh%')
         or lower(mi.generic_name) like lower('%?t%')
      order by m.meal_id
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)){
        while(rs.next()){
          System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | ing="+rs.getString(3)+" | product="+rs.getString(4)+" | status="+rs.getString(5));
        }
      }
    }
  }
}
