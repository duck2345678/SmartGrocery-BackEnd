import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class CheckMealData {
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
      select m.meal_id,m.meal_name,mi.role,mi.generic_name,p.product_id,p.product_name
      from meals m
      left join meal_ingredients mi on mi.meal_id=m.meal_id
      left join products p on p.product_id=mi.product_id
      where lower(m.meal_name) like lower('%canh cai%thit bam%')
      order by m.meal_id, mi.role desc, mi.meal_ingredient_id
      """;
      try(Statement st=c.createStatement(); ResultSet rs=st.executeQuery(sql)){
        boolean any=false;
        while(rs.next()){
          any=true;
          System.out.println(rs.getLong(1)+" | "+rs.getString(2)+" | role="+rs.getString(3)+" | generic="+rs.getString(4)+" | pid="+rs.getString(5)+" | product="+rs.getString(6));
        }
        if(!any) System.out.println("No meal found.");
      }
    }
  }
}
