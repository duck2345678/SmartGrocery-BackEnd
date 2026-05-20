import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class CheckMeal144 {
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
      select m.meal_id,m.meal_name,mi.role,mi.generic_name,p.product_name,p.status
      from meals m
      left join meal_ingredients mi on mi.meal_id=m.meal_id
      left join products p on p.product_id=mi.product_id
      where m.meal_id=144
      order by mi.role desc, mi.meal_ingredient_id
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)){
        while(rs.next()){
          System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | role="+rs.getString(3)+" | generic="+rs.getString(4)+" | product="+rs.getString(5)+" | status="+rs.getString(6));
        }
      }

      String stockSql="""
      select p.product_id,p.product_name,coalesce(sum(coalesce(s.available_quantity,0)),0) as qty
      from meal_ingredients mi
      join products p on p.product_id=mi.product_id
      left join product_variants v on v.product_id=p.product_id and upper(v.status)='ACTIVE'
      left join inventory_stock s on s.variant_id=v.variant_id
      where mi.meal_id=144 and upper(p.status)='ACTIVE'
      group by p.product_id,p.product_name
      order by p.product_id
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(stockSql)){
        System.out.println("-- stock --");
        while(rs.next()){
          System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | qty="+rs.getLong(3));
        }
      }
    }
  }
}
