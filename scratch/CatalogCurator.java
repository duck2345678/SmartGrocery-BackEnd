import java.sql.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class CatalogCurator {
  record Cat(String code, String name, String desc, int sort) {}
  record ProductRow(long id, String code, String name, String shortDesc, String desc, long categoryId, String categoryCode, String categoryName) {}
  record VariantRow(long id, long productId, String variantName, String size, String unit, String packageSize, Integer weightGram) {}
  record ProductDecision(long productId, String productCode, String productName, String oldCategoryCode, String oldCategoryName, String newCategoryCode, String reason, int confidence) {}
  record VariantDecision(long variantId, long productId, String oldUnit, String newUnit, String oldPackageSize, String newPackageSize, Integer oldWeightGram, Integer newWeightGram, String reason) {}

  static final List<Cat> TAXONOMY = List.of(
    new Cat("CAT_VEG_LEAF", "Rau ăn lá", "Rau ăn lá và rau thơm tươi", 10),
    new Cat("CAT_VEG_ROOT_FRUIT", "Củ, quả", "Rau củ quả dùng nấu ăn", 20),
    new Cat("CAT_MUSHROOM_BEAN", "Nấm & đậu", "Nấm, đậu phụ, đậu tươi", 30),
    new Cat("CAT_FRUIT_LOCAL", "Trái cây nội địa", "Trái cây phổ biến trong nước", 40),
    new Cat("CAT_FRUIT_IMPORTED", "Trái cây nhập khẩu", "Trái cây nhập khẩu hoặc giống nhập", 50),
    new Cat("CAT_PORK", "Thịt heo", "Thịt heo tươi, xay, cắt khay", 60),
    new Cat("CAT_BEEF", "Thịt bò", "Thịt bò tươi, xay, cắt lát", 70),
    new Cat("CAT_POULTRY_EGG", "Gia cầm & Trứng", "Gà, vịt và các loại trứng", 80),
    new Cat("CAT_SEAFOOD_FRESH", "Hải sản tươi", "Cá, tôm, mực, lươn, nghêu sò tươi", 90),
    new Cat("CAT_SEAFOOD_FROZEN", "Hải sản đông lạnh", "Hải sản cấp đông/IQF", 100),
    new Cat("CAT_PROCESSED_FOOD", "Thực phẩm chế biến", "Đồ ăn sơ chế, xúc xích, chả, đồ tiện lợi", 110),
    new Cat("CAT_RICE_NOODLE", "Gạo & Bún khô", "Gạo, bún, phở, miến, mì khô", 120),
    new Cat("CAT_GRAIN_NUT", "Ngũ cốc & Hạt", "Ngũ cốc, yến mạch, các loại hạt", 130),
    new Cat("CAT_SPICE", "Gia vị cơ bản", "Muối, đường, tiêu, nghệ, ớt, bột gia vị", 140),
    new Cat("CAT_SAUCE_OIL", "Nước sốt & Dầu", "Dầu ăn, nước mắm, nước tương, tương ớt, sốt", 150),
    new Cat("CAT_CANNED_FOOD", "Thực phẩm đóng hộp", "Đồ hộp, lon, hũ bảo quản", 160),
    new Cat("CAT_VEGETARIAN", "Thực phẩm chay", "Thực phẩm chay và thay thế thịt", 170),
    new Cat("CAT_DAIRY", "Sữa & Chế phẩm", "Sữa, sữa chua, phô mai, bơ", 180),
    new Cat("CAT_BOTTLED_DRINK", "Thức uống đóng chai", "Nước lọc, nước ép, nước ngọt, nước khoáng", 190),
    new Cat("CAT_TEA_COFFEE", "Trà & Cà phê", "Trà, cà phê, cacao pha uống", 200),
    new Cat("CAT_SNACK", "Đồ ăn vặt", "Snack mặn, rong biển, khô ăn liền", 210),
    new Cat("CAT_SWEET_BAKERY", "Đồ ngọt & Bánh", "Bánh, kẹo, chocolate, đồ ngọt", 220),
    new Cat("CAT_HOUSEHOLD", "Gia dụng", "Vật dụng gia đình, vệ sinh nhà cửa", 230),
    new Cat("CAT_PERSONAL_BABY", "Chăm sóc cá nhân & Em bé", "Mỹ phẩm, vệ sinh cá nhân, đồ em bé", 240)
  );
  static final Set<String> TAXONOMY_CODES = new HashSet<>();
  static { for (Cat c : TAXONOMY) TAXONOMY_CODES.add(c.code); }

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

  static String nz(String s) { return s == null ? "" : s; }
  static String ascii(String s) {
    String n = Normalizer.normalize(nz(s).toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return n.replace('đ','d').replace('Đ','d');
  }
  static boolean has(String t, String... keys) { for (String k : keys) if (t.contains(k)) return true; return false; }
  static boolean re(String t, String regex) { return Pattern.compile(regex).matcher(t).find(); }

  static ProductDecision classify(ProductRow p) {
    String t = ascii(p.name() + " " + p.shortDesc() + " " + p.desc() + " " + p.code());
    String code = null, reason = "", old = p.categoryCode();
    int conf = 80;

    if (has(t, "nuoc mam", "nuoc tuong", "tuong ot", "tuong ca", "dau an", "dau oliu", "dau olive", "sot ", "xot ", "mayonnaise", "dau hao", "sa te", "giấm", "giam")) { code="CAT_SAUCE_OIL"; reason="sauce/oil keyword"; conf=96; }
    else if (has(t, "muoi", "duong", "tieu", "bot nghe", "nghe", "ot hiem", "ot bot", "bot canh", "hat nem", "gia vi", "que", "hoi", "sa ", "gừng", "gung", "toi ", "hanh kho", "bot ngot")) { code="CAT_SPICE"; reason="spice keyword"; conf=94; }
    else if (has(t, "ca phe", "cafe", "coffee", "tra ", "tra xanh", "hong tra", "cacao")) { code="CAT_TEA_COFFEE"; reason="tea/coffee keyword"; conf=95; }
    else if (has(t, "sua", "yogurt", "sua chua", "pho mai", "phomai", "cheese", "bo lat", "butter", "kem tuoi")) { code="CAT_DAIRY"; reason="dairy keyword"; conf=94; }
    else if (has(t, "nuoc suoi", "nuoc khoang", "nuoc ep", "nuoc ngot", "coca", "pepsi", "sprite", "sting", "tra xanh khong do", "lon bia", "bia ")) { code="CAT_BOTTLED_DRINK"; reason="drink keyword"; conf=92; }
    else if (has(t, "banh", "keo", "chocolate", "socola", "biscuit", "cookie", "wafer", "kem hop", "thach")) { code="CAT_SWEET_BAKERY"; reason="sweet/bakery keyword"; conf=91; }
    else if (has(t, "snack", "kho ga", "kho bo", "rong bien", "bim bim", "hat dieu rang", "dau phong rang", "an vat")) { code="CAT_SNACK"; reason="snack keyword"; conf=90; }
    else if (has(t, "gao", "bun", "pho kho", "mien", "mi goi", "mi an lien", "mi kho", "nui", "pasta")) { code="CAT_RICE_NOODLE"; reason="rice/noodle keyword"; conf=95; }
    else if (has(t, "yen mach", "ngu coc", "granola", "hat chia", "hat dieu", "hanh nhan", "oc cho", "dau xanh", "dau den", "dau do", "me den", "hat sen")) { code="CAT_GRAIN_NUT"; reason="grain/nut keyword"; conf=90; }
    else if (has(t, "do hop", "dong hop", "ca hop", "thit hop", "pate", "lon ", "hop thiec", "ca ngu dong hop")) { code="CAT_CANNED_FOOD"; reason="canned keyword"; conf=92; }
    else if (has(t, "chay", "dau hu", "dau phu", "tofu", "nam huong chay", "cha chay", "thit chay")) { code="CAT_VEGETARIAN"; reason="vegetarian keyword"; conf=88; }
    else if (has(t, "xuc xich", "cha lua", "gio lua", "gio song", "ca vien", "bo vien", "surimi", "kim chi", "dua cai", "do chua", "nem chua", "thit nguoi", "ham", "jambon")) { code="CAT_PROCESSED_FOOD"; reason="processed food keyword"; conf=90; }
    else if (has(t, "dong lanh", "iqf", "frozen", "cap dong")) {
      if (has(t, "ca ", "tom", "muc", "so diep", "ngheu", "hau", "hai san")) { code="CAT_SEAFOOD_FROZEN"; reason="frozen seafood keyword"; conf=97; }
      else { code="CAT_PROCESSED_FOOD"; reason="frozen processed keyword"; conf=82; }
    }
    else if (has(t, "tom", "muc", "ca loc", "ca hoi", "ca basa", "ca dieu hong", "ca ngu", "ngheu", "so ", "so diep", "hau", "bach tuoc", "luon", "hai san")) { code="CAT_SEAFOOD_FRESH"; reason="fresh seafood keyword"; conf=92; }
    else if (has(t, "thit heo", "heo", "ba roi", "cot let", "suon", "nac dam", "chan gio", "gio heo", "xay heo")) { code="CAT_PORK"; reason="pork keyword"; conf=95; }
    else if (has(t, "thit bo", "bo xay", "bap bo", "than bo", "uc bo", "nam bo", "bo uc", "ribeye", "sirloin")) { code="CAT_BEEF"; reason="beef keyword"; conf=95; }
    else if (has(t, "ga ", "uc ga", "dui ga", "canh ga", "vit ", "trung", "trung ga", "trung cut")) { code="CAT_POULTRY_EGG"; reason="poultry/egg keyword"; conf=93; }
    else if (has(t, "tao", "le ", "nho", "cherry", "kiwi", "blueberry", "dau tay", "cam navel", "uc", "my ", "new zealand", "han quoc", "nhat", "nhap khau")) { code="CAT_FRUIT_IMPORTED"; reason="imported fruit keyword"; conf=86; }
    else if (has(t, "chuoi", "xoai", "dua hau", "dua luoi", "thanh long", "buoi", "cam ", "quyt", "oi", "coc", "man", "vai", "nhan", "sau rieng", "mit", "chom chom", "dua ", "trai cay")) { code="CAT_FRUIT_LOCAL"; reason="local fruit keyword"; conf=86; }
    else if (has(t, "nam", "dau bap", "bap non", "dau que", "dau dua", "dau ha lan", "gia do")) { code="CAT_MUSHROOM_BEAN"; reason="mushroom/bean keyword"; conf=88; }
    else if (has(t, "rau", "cai", "xa lach", "salad", "mong toi", "muong", "can tay", "hanh la", "ngo gai", "rau thom", "hung", "tia to", "kinh gioi", "rau ram", "rau den", "rau ngot", "bap cai")) { code="CAT_VEG_LEAF"; reason="leaf vegetable keyword"; conf=88; }
    else if (has(t, "ca chua", "dua leo", "dua chuot", "kho qua", "bi do", "bi ngoi", "ca rot", "khoai", "su hao", "cu cai", "cu den", "cu sen", "bau", "muop", "ot chuong", "ot ", "hanh tay", "bong cai", "sup lo", "ngo ", "bap ", "cu qua")) { code="CAT_VEG_ROOT_FRUIT"; reason="root/fruit vegetable keyword"; conf=88; }
    else if (has(t, "nuoc rua chen", "bot giat", "nuoc giat", "nuoc lau san", "giay ve sinh", "khan giay", "tui rac", "mieng rua chen", "nuoc xa", "gia dung", "pin ", "den ", "mang boc", "hop dung", "noi ", "chao ", "ly ", "chen ", "dia ")) { code="CAT_HOUSEHOLD"; reason="household keyword"; conf=93; }
    else if (has(t, "dau goi", "sua tam", "kem danh rang", "ban chai", "ta em be", "tam bong", "khan uot", "dao cao", "nuoc rua tay", "sap thom", "lan khu mui", "dung dich ve sinh")) { code="CAT_PERSONAL_BABY"; reason="personal/baby keyword"; conf=93; }

    if (code == null) {
      if (old != null && TAXONOMY_CODES.contains(old)) { code = old; reason = "kept existing taxonomy category"; conf = 70; }
      else if (old != null && old.equals("CAT_VEG")) { code="CAT_VEG_ROOT_FRUIT"; reason="legacy vegetable fallback"; conf=55; }
      else if (old != null && old.equals("CAT_FRUIT")) { code="CAT_FRUIT_LOCAL"; reason="legacy fruit fallback"; conf=55; }
      else if (old != null && old.equals("CAT_MEAT")) { code="CAT_PROCESSED_FOOD"; reason="legacy meat fallback requires review"; conf=45; }
      else if (old != null && old.equals("CAT_STAPLE")) { code="CAT_RICE_NOODLE"; reason="legacy staple fallback requires review"; conf=45; }
      else { code="CAT_PROCESSED_FOOD"; reason="unmatched fallback requires review"; conf=35; }
    }
    return new ProductDecision(p.id(), p.code(), p.name(), p.categoryCode(), p.categoryName(), code, reason, conf);
  }

  static String extractPackage(String text) {
    String s = nz(text).replace(',', '.');
    Pattern p = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|g|gram|gr|l|lit|liter|ml|quả|qua|cái|cai|chiếc|chiec|lon|chai|gói|goi|hộp|hop|túi|tui|bó|bo|khay|vỉ|vi|cuộn|cuon)");
    Matcher m = p.matcher(s);
    String last = null;
    while (m.find()) last = m.group().trim();
    if (last != null) return last.replaceAll("(?i)gram|gr", "g").replaceAll("(?i)lit|liter", "l");
    return null;
  }

  static Integer extractWeightGram(String pkg) {
    if (pkg == null) return null;
    Matcher m = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|g)").matcher(pkg.replace(',', '.'));
    if (m.find()) {
      double v = Double.parseDouble(m.group(1));
      String u = m.group(2).toLowerCase(Locale.ROOT);
      return (int)Math.round(u.equals("kg") ? v * 1000 : v);
    }
    return null;
  }

  static String inferUnit(String text, String pkg) {
    String t = ascii(text + " " + nz(pkg));
    if (has(t, "khay")) return "khay";
    if (has(t, "hop", "hộp")) return "hộp";
    if (has(t, "chai")) return "chai";
    if (has(t, "lon")) return "lon";
    if (has(t, "tui", "túi")) return "túi";
    if (has(t, "goi", "gói")) return "gói";
    if (has(t, "bo ", "bó")) return "bó";
    if (has(t, "vi ", "vỉ")) return "vỉ";
    if (has(t, "cuon", "cuộn")) return "cuộn";
    if (has(t, "hu ", "hũ")) return "hũ";
    if (has(t, "tuyp", "tuýp")) return "tuýp";
    if (re(t, "\\bkg\\b")) return "kg";
    if (re(t, "\\bqua\\b")) return "quả";
    if (re(t, "\\bcai\\b|\\bchiec\\b")) return "cái";
    return "gói";
  }

  static VariantDecision curateVariant(ProductRow p, VariantRow v) {
    String source = p.name() + " " + nz(v.variantName()) + " " + nz(v.size()) + " " + nz(v.packageSize());
    String pkg = extractPackage(source);
    String newPkg = pkg != null ? pkg : v.packageSize;
    String newUnit = inferUnit(source, newPkg);
    Integer newWeight = extractWeightGram(newPkg);
    if (newWeight == null) newWeight = v.weightGram;
    String reason = "unit/package inferred from product and variant text";
    return new VariantDecision(v.id(), p.id(), v.unit(), newUnit, v.packageSize(), newPkg, v.weightGram(), newWeight, reason);
  }

  static String q(String s) { return s == null ? "" : s.replace("\t", " ").replace("\r", " ").replace("\n", " "); }

  public static void main(String[] args) throws Exception {
    boolean apply = Arrays.asList(args).contains("--apply");
    Map<String,String> e = env();
    Path report = Paths.get("scratch", apply ? "catalog-curation-applied.tsv" : "catalog-curation-plan.tsv");
    try (Connection c = DriverManager.getConnection(e.get("SUPABASE_DB_URL"), e.get("SUPABASE_DB_USERNAME"), e.get("SUPABASE_DB_PASSWORD"))) {
      c.setAutoCommit(false);
      Map<Long,ProductRow> products = new LinkedHashMap<>();
      try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
        select p.product_id,p.product_code,p.product_name,p.short_description,p.description,p.category_id,c.category_code,c.category_name
        from products p left join categories c on c.category_id=p.category_id order by p.product_id
      """)) {
        while (rs.next()) products.put(rs.getLong(1), new ProductRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8)));
      }
      Map<Long,List<VariantRow>> variants = new HashMap<>();
      try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select variant_id,product_id,variant_name,size,unit,package_size,weight_gram from product_variants order by variant_id")) {
        while (rs.next()) variants.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(new VariantRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), (Integer)rs.getObject(7)));
      }

      List<ProductDecision> pds = new ArrayList<>();
      List<VariantDecision> vds = new ArrayList<>();
      for (ProductRow p : products.values()) {
        ProductDecision pd = classify(p); pds.add(pd);
        for (VariantRow v : variants.getOrDefault(p.id, List.of())) vds.add(curateVariant(p, v));
      }

      List<String> lines = new ArrayList<>();
      lines.add("TYPE\tID\tPRODUCT_CODE\tPRODUCT_NAME\tOLD_CATEGORY\tNEW_CATEGORY\tCONFIDENCE\tOLD_UNIT\tNEW_UNIT\tOLD_PACKAGE\tNEW_PACKAGE\tOLD_WEIGHT_G\tNEW_WEIGHT_G\tREASON");
      for (ProductDecision d : pds) lines.add(String.join("\t", "PRODUCT", ""+d.productId, q(d.productCode), q(d.productName), q(d.oldCategoryCode), q(d.newCategoryCode), ""+d.confidence, "", "", "", "", "", "", q(d.reason)));
      for (VariantDecision d : vds) lines.add(String.join("\t", "VARIANT", ""+d.variantId, "", "", "", "", "", q(d.oldUnit), q(d.newUnit), q(d.oldPackageSize), q(d.newPackageSize), q(d.oldWeightGram == null ? null : d.oldWeightGram.toString()), q(d.newWeightGram == null ? null : d.newWeightGram.toString()), q(d.reason)));
      Files.write(report, lines, java.nio.charset.StandardCharsets.UTF_8);

      Map<String,Long> categoryIds = new HashMap<>();
      if (apply) {
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        try (Statement st = c.createStatement()) {
          st.executeUpdate("create table catalog_curation_backup_products_" + suffix + " as select * from products");
          st.executeUpdate("create table catalog_curation_backup_product_variants_" + suffix + " as select * from product_variants");
          st.executeUpdate("create table catalog_curation_backup_categories_" + suffix + " as select * from categories");
        }
        try (PreparedStatement ps = c.prepareStatement("""
          insert into categories(category_code, category_name, description, sort_order, is_active, created_at, updated_at)
          values (?, ?, ?, ?, true, now(), now())
          on conflict (category_code) do update set category_name=excluded.category_name, description=excluded.description, sort_order=excluded.sort_order, is_active=true, updated_at=now()
        """)) {
          for (Cat cat : TAXONOMY) { ps.setString(1, cat.code); ps.setString(2, cat.name); ps.setString(3, cat.desc); ps.setInt(4, cat.sort); ps.addBatch(); }
          ps.executeBatch();
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select category_id, category_code from categories")) {
          while (rs.next()) categoryIds.put(rs.getString(2), rs.getLong(1));
        }
        try (PreparedStatement ps = c.prepareStatement("update products set category_id=?, updated_at=now() where product_id=?")) {
          for (ProductDecision d : pds) { if (d.confidence < 70) continue; ps.setLong(1, categoryIds.get(d.newCategoryCode)); ps.setLong(2, d.productId); ps.addBatch(); }
          ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement("update product_variants set unit=?, package_size=?, weight_gram=?, updated_at=now() where variant_id=?")) {
          for (VariantDecision d : vds) {
            ps.setString(1, d.newUnit); ps.setString(2, d.newPackageSize);
            if (d.newWeightGram == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, d.newWeightGram);
            ps.setLong(4, d.variantId); ps.addBatch();
          }
          ps.executeBatch();
        }
        String placeholders = String.join(",", Collections.nCopies(TAXONOMY.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement("update categories set is_active=false, updated_at=now() where category_code not in (" + placeholders + ")")) {
          int i=1; for (Cat cat : TAXONOMY) ps.setString(i++, cat.code); ps.executeUpdate();
        }
      }
      if (apply) c.commit(); else c.rollback();
      System.out.println((apply ? "APPLIED" : "DRY_RUN") + " products=" + pds.size() + " variants=" + vds.size() + " report=" + report);
      Map<String,Long> counts = new TreeMap<>();
      for (ProductDecision d : pds) counts.merge(d.newCategoryCode, 1L, Long::sum);
      for (Map.Entry<String,Long> en : counts.entrySet()) System.out.println(en.getKey() + "\t" + en.getValue());
      long low = pds.stream().filter(d -> d.confidence < 70).count();
      System.out.println("LOW_CONFIDENCE_PRODUCTS\t" + low);
    }
  }
}