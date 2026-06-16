package com.smartgrocery.backend.service.ai;

import com.smartgrocery.backend.entity.Meal;
import com.smartgrocery.backend.entity.MealIngredient;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.IngredientAlias;
import com.smartgrocery.backend.repository.jpa.MealIngredientRepository;
import com.smartgrocery.backend.repository.jpa.MealRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import com.smartgrocery.backend.repository.jpa.IngredientAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealDatabaseService {

    public enum IngredientRole {
        PRIMARY,
        SECONDARY
    }

    public record CatalogMealIngredient(String name, Long productId, IngredientRole role) {}

    public record CatalogMealOption(
            long catalogItemId,
            int optionNo,
            String title,
            List<String> ingredients,
            List<CatalogMealIngredient> ingredientDetails,
            String reason
    ) {
        public List<String> primaryIngredients() {
            return ingredientNamesByRole(IngredientRole.PRIMARY);
        }

        public List<Long> primaryProductIds() {
            return ingredientDetails.stream()
                    .filter(Objects::nonNull)
                    .filter(ingredient -> IngredientRole.PRIMARY.equals(ingredient.role()))
                    .map(CatalogMealIngredient::productId)
                    .filter(Objects::nonNull)
                    .toList();
        }

        public List<String> secondaryIngredients() {
            return ingredientNamesByRole(IngredientRole.SECONDARY);
        }

        public List<String> ingredientNamesByRole(IngredientRole role) {
            if (ingredientDetails == null || ingredientDetails.isEmpty() || role == null) {
                return List.of();
            }
            return ingredientDetails.stream()
                    .filter(Objects::nonNull)
                    .filter(ingredient -> role.equals(ingredient.role()))
                    .map(CatalogMealIngredient::name)
                    .toList();
        }
    }

    private final MealRepository mealRepository;
    private final MealIngredientRepository mealIngredientRepository;
    private final ProductRepository productRepository;
    private final QuantityParsingService quantityParsingService;
    private final IngredientAliasRepository ingredientAliasRepository;
    private final IngredientTextNormalizer ingredientTextNormalizer;

    /**
     * "Biên soạn thực đơn" from products.
     * This is the "Bootstrapping" phase where we turn Students (Products) into Classes (Meals).
     */
    @Transactional
    public void bootstrapFromProducts() {
        log.info("Starting mass meal compilation (100+ items, Class & Students architecture)...");
        
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            log.warn("No products found in database. Cannot compile meals.");
            return;
        }

        mealIngredientRepository.deleteAll();
        mealRepository.deleteAll();
        // --- GROUP 1: BỮA SÁNG (BREAKFAST) ---
        createBatch(products, "Sáng", "Bình thường", List.of(
            new MealDef("Phở Bò Truyền Thống Sáng", "Phở bò thơm ngon đúng điệu.", List.of("Thăn bò Úc", "Bắp bò hoa", "Bánh phở khô"), List.of("Ngò rí & Hành lá (Combo)", "Giá đỗ tươi", "Chanh không hạt")),
            new MealDef("Bún Gà Thanh Đạm Sáng", "Bữa sáng nhẹ nhàng với gà.", List.of("Đùi gà góc tư", "Bún tươi sấy khô"), List.of("Hành lá", "Giá đỗ tươi", "Chanh không hạt")),
            new MealDef("Bánh Mì Pate Chả Lụa", "Món ăn đường phố tiện lợi.", List.of("Bánh mì ổ", "Chả lụa que", "Pate gan heo"), List.of("Dưa leo baby", "Ớt hiểm trái", "Ngò rí & Hành lá (Combo)")),
            new MealDef("Cháo Thịt Bằm Hành Lá", "Ấm bụng ngày mới.", List.of("Thịt heo xay", "Gạo ST25"), List.of("Hành lá", "Tiêu đen xay", "Gừng già")),
            new MealDef("Mì Tôm Hảo Hảo Trứng", "Nhanh gọn, đủ chất.", List.of("Mì tôm Hảo Hảo", "Trứng gà ta (Hộp 10 quả)"), List.of("Hành lá", "Ớt hiểm trái")),
            new MealDef("Xôi Gà Nấm Đùi Gà", "Xôi dẻo thơm ngon.", List.of("Đùi gà góc tư", "Nấm đùi gà", "Gạo nếp nương"), List.of("Hành lá", "Nước tương đậu nành")),
            new MealDef("Bánh Bao Nhân Thịt Sáng", "Bữa sáng truyền thống.", List.of("Bánh bao nhân thịt trứng cút"), List.of("Tương ớt Chinsu")),
            new MealDef("Hủ Tiếu Nam Vang Sáng", "Hương vị đặc trưng.", List.of("Tôm thẻ chân trắng", "Ba rọi heo (Ba chỉ)", "Hủ tiếu khô"), List.of("Giá đỗ tươi", "Hành lá", "Chanh không hạt")),
            new MealDef("Bún Cá Thu Sáng", "Vị biển đậm đà.", List.of("Cá thu cắt khúc", "Bún tươi sấy khô"), List.of("Rau thì là", "Hành lá", "Ớt hiểm trái")),
            new MealDef("Mì Xào Bò Sáng", "Năng lượng cho ngày mới.", List.of("Thăn bò Úc", "Mì tôm Hảo Hảo"), List.of("Ớt chuông đủ màu", "Hành tây")),
            new MealDef("Bún Mọc Đùi Gà", "Vị ngon ngọt dịu.", List.of("Đùi gà góc tư", "Giò sống", "Bún tươi sấy khô"), List.of("Hành lá", "Giá đỗ tươi")),
            new MealDef("Miến Gà Sáng", "Bữa sáng ấm áp.", List.of("Đùi gà góc tư", "Miến dong"), List.of("Hành lá", "Gừng già")),
            new MealDef("Cháo Sườn Heo Sáng", "Dễ ăn, bổ dưỡng.", List.of("Sườn non heo", "Gạo ST25"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Bánh Mì Ốp La Pate", "Nhanh gọn lẹ.", List.of("Bánh mì ổ", "Trứng gà ta (Hộp 10 quả)", "Pate gan heo"), List.of("Dưa leo baby", "Ớt hiểm trái")),
            new MealDef("Súp Gà Ngô Non Sáng", "Món khai vị nhẹ nhàng.", List.of("Đùi gà góc tư", "Trứng gà ta (Hộp 10 quả)"), List.of("Bắp ngọt (Ngô Mỹ)", "Hành lá", "Tiêu đen xay")),
            new MealDef("Hủ Tiếu Gà Sáng", "Thơm ngon vị gà.", List.of("Đùi gà góc tư", "Hủ tiếu khô"), List.of("Giá đỗ tươi", "Hành lá")),
            new MealDef("Cháo Cá Lóc Hành Gừng", "Bữa sáng ấm bụng.", List.of("Cá lóc đồng làm sẵn", "Gạo ST25"), List.of("Hành lá", "Gừng già")),
            new MealDef("Bánh Canh Chả Cá Sáng", "Đậm đà vị miền Trung.", List.of("Chả cá thát lát", "Sợi bánh canh tươi"), List.of("Hành lá", "Ớt hiểm trái")),
            new MealDef("Nui Xào Thịt Bằm", "Trẻ em yêu thích.", List.of("Thịt heo xay", "Nui ống (Macaroni)"), List.of("Cà chua beef", "Hành lá")),
            new MealDef("Cháo Tim Cật Sáng", "Bồi bổ sức khỏe.", List.of("Tim heo", "Cật heo tươi", "Gạo ST25"), List.of("Hành lá", "Tiêu đen xay"))
        ));

        createBatch(products, "Sáng", "Eatclean", List.of(
            new MealDef("Yến Mạch Sữa Hạnh Nhân", "Bữa sáng lành mạnh.", List.of("Ngũ cốc yến mạch", "Sữa hạnh nhân không đường"), List.of("Chuối già Nam Mỹ", "Hạt chia Úc")),
            new MealDef("Trứng Luộc & Salad Sáng", "Protein sạch.", List.of("Trứng gà ta (Hộp 10 quả)", "Xà lách lolo xanh"), List.of("Cà chua beef", "Dầu ăn hướng dương")),
            new MealDef("Sữa Chua Trái Cây Sáng", "Giải nhiệt, đẹp da.", List.of("Sữa chua không đường", "Táo Envy"), List.of("Việt quất tươi", "Dâu tây Đà Lạt")),
            new MealDef("Khoai Lang Luộc & Trứng", "Tinh bột chậm.", List.of("Khoai lang mật", "Trứng gà ta (Hộp 10 quả)"), List.of("Muối i-ốt")),
            new MealDef("Bơ Sáp & Sandwich Sáng", "Chất béo tốt.", List.of("Bơ sáp", "Phô mai lát (Sandwich)", "Bánh mì Sandwich"), List.of("Cà chua beef", "Tiêu đen xay")),
            new MealDef("Sinh Tố Chuối Yến Mạch", "Nạp nhanh năng lượng.", List.of("Ngũ cốc yến mạch", "Chuối già Nam Mỹ"), List.of("Sữa hạnh nhân không đường", "Hạt chia Úc")),
            new MealDef("Cháo Yến Mạch Thịt Bằm", "Lành mạnh và nhẹ bụng.", List.of("Ngũ cốc yến mạch", "Thịt heo xay"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Khoai Lang & Sữa Hạnh Nhân", "Nhanh gọn, ít calo.", List.of("Khoai lang mật", "Sữa hạnh nhân không đường"), List.of("Chuối già Nam Mỹ")),
            new MealDef("Ức Gà Áp Chảo Sáng", "Protein cao cho gym.", List.of("Đùi gà góc tư"), List.of("Xà lách lolo xanh", "Dầu ăn hướng dương")),
            new MealDef("Salad Trứng Bơ Sáng", "Chất béo tốt và xơ.", List.of("Trứng gà ta (Hộp 10 quả)", "Bơ sáp"), List.of("Xà lách lolo xanh", "Tiêu đen xay")),
            new MealDef("Yến Mạch Việt Quất Sáng", "Chống oxy hóa.", List.of("Ngũ cốc yến mạch"), List.of("Sữa hạnh nhân không đường", "Việt quất tươi")),
            new MealDef("Sandwich Bơ Trứng Sáng", "Cân bằng dinh dưỡng.", List.of("Bơ sáp", "Trứng gà ta (Hộp 10 quả)", "Bánh mì Sandwich"), List.of("Cà chua beef", "Tiêu đen xay"))
        ));

        createBatch(products, "Sáng", "Chay", List.of(
            new MealDef("Bún Chay Đậu Hũ Sáng", "Bữa sáng thanh tịnh.", List.of("Đậu hũ non", "Bún tươi sấy khô"), List.of("Nấm đùi gà", "Nước tương đậu nành")),
            new MealDef("Bánh Mì Chay Sáng", "Tiện lợi và thanh đạm.", List.of("Bánh mì ổ", "Chả lụa chay", "Đậu hũ non"), List.of("Dưa leo baby", "Hành lá")),
            new MealDef("Cháo Nấm Đùi Gà Chay", "Bổ dưỡng cho người ăn chay.", List.of("Nấm đùi gà", "Gạo ST25"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Miến Nấm Chay Sáng", "Sáng thanh mát.", List.of("Nấm kim châm", "Miến dong"), List.of("Đậu hũ non", "Hành lá")),
            new MealDef("Súp Nấm Đậu Hũ Sáng", "Dễ tiêu hóa.", List.of("Nấm mỡ tươi", "Đậu hũ non"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Bánh Mì Pate Chay", "Thơm ngon tiện lợi.", List.of("Bánh mì ổ", "Chả lụa chay"), List.of("Dưa leo baby")),
            new MealDef("Cháo Yến Mạch Hạt Sen", "An thần ngày mới.", List.of("Ngũ cốc yến mạch", "Hạt sen tươi"), List.of("Sữa chua không đường")),
            new MealDef("Bún Riêu Chay Sáng", "Hương vị đồng quê.", List.of("Đậu hũ non", "Bún tươi sấy khô"), List.of("Cà chua beef", "Hành lá")),
            new MealDef("Mì Quảng Chay Sáng", "Đặc sản miền Trung.", List.of("Đậu hũ non", "Sợi mì Quảng tươi"), List.of("Đậu phộng rang", "Nước tương đậu nành")),
            new MealDef("Cháo Đậu Đỏ Chay Sáng", "Thanh lọc cơ thể.", List.of("Gạo ST25"), List.of("Sữa hạnh nhân không đường"))
        ));

        // --- GROUP 2: BỮA TRƯA (LUNCH) ---
        createBatch(products, "Trưa", "Bình thường", List.of(
            new MealDef("Cơm Tấm Sườn Nướng Trưa", "Món cơm quốc dân.", List.of("Sườn non heo", "Gạo tấm"), List.of("Trứng gà ta (Hộp 10 quả)", "Dưa leo baby", "Nước mắm cá cơm")),
            new MealDef("Cơm Gà Hải Nam Trưa", "Gà luộc thơm lừng.", List.of("Đùi gà góc tư", "Gạo ST25"), List.of("Gừng già", "Dưa leo baby", "Hành lá")),
            new MealDef("Bún Thịt Nướng Chả Giò", "Vị ngon khó cưỡng.", List.of("Ba rọi heo (Ba chỉ)", "Bún tươi sấy khô"), List.of("Chả giò tôm cua", "Xà lách lolo xanh", "Đậu phộng rang")),
            new MealDef("Mì Ý Sốt Bò Bằm Trưa", "Phong cách Ý nhẹ nhàng.", List.of("Thịt heo xay", "Sợi mì Ý (Spaghetti)"), List.of("Cà chua beef", "Hành tây", "Dầu ăn hướng dương")),
            new MealDef("Cơm Bò Lúc Lắc Trưa", "Bò mềm, sốt đậm đà.", List.of("Thăn bò Úc", "Khoai tây Đà Lạt"), List.of("Ớt chuông đủ màu", "Hành tây", "Tiêu đen xay")),
            new MealDef("Hủ Tiếu Xào Hải Sản Trưa", "Hải sản tươi ngon.", List.of("Mực ống tươi", "Tôm thẻ chân trắng", "Hủ tiếu khô"), List.of("Giá đỗ tươi", "Hành tây")),
            new MealDef("Cơm Chiên Dương Châu", "Đầy đủ màu sắc.", List.of("Trứng gà ta (Hộp 10 quả)", "Gạo ST25"), List.of("Cà rốt", "Hành lá")),
            new MealDef("Cơm Sườn Ram Mặn", "Món trưa đậm đà.", List.of("Sườn non heo", "Gạo ST25"), List.of("Ớt hiểm trái", "Hành lá")),
            new MealDef("Bún Chả Hà Nội Trưa", "Hương vị thủ đô.", List.of("Ba rọi heo (Ba chỉ)", "Bún tươi sấy khô"), List.of("Dưa leo baby", "Rau thơm tổng hợp")),
            new MealDef("Mì Xào Giòn Tôm Mực", "Đầy đặn hải sản.", List.of("Tôm thẻ chân trắng", "Mực ống tươi"), List.of("Hành tây", "Giá đỗ tươi")),
            new MealDef("Cơm Bò Xào Bông Cải", "Bổ dưỡng, ngon miệng.", List.of("Thăn bò Úc", "Gạo ST25"), List.of("Bông cải xanh (Súp lơ)", "Hành tây")),
            new MealDef("Cơm Cá Thu Kho Thơm", "Hao cơm ngày nắng.", List.of("Cá thu cắt khúc", "Gạo ST25"), List.of("Dứa (Thơm)", "Hành lá")),
            new MealDef("Cơm Thịt Kho Quẹt Trưa", "Đậm đà hương vị.", List.of("Ba rọi heo (Ba chỉ)", "Gạo ST25"), List.of("Ớt hiểm trái", "Hành lá")),
            new MealDef("Bún Đậu Mắm Tôm Trưa", "Món ngon dân dã.", List.of("Đậu hũ non", "Bún tươi sấy khô"), List.of("Chả lụa que", "Dưa leo baby", "Mắm tôm Bắc")),
            new MealDef("Cơm Chiên Tỏi Đùi Gà", "Giòn ngon hấp dẫn.", List.of("Đùi gà góc tư", "Gạo ST25"), List.of("Hành lá", "Ớt hiểm trái"))
        ));

        createBatch(products, "Trưa", "Đồ Âu", List.of(
            new MealDef("Steak Bò Úc Sốt Tiêu", "Đẳng cấp nhà hàng.", List.of("Thăn bò Úc", "Bơ lạt tự nhiên"), List.of("Khoai tây Đà Lạt", "Măng tây", "Tiêu đen xay")),
            new MealDef("Pizza Hải Sản Trưa", "Tiện lợi, ngon miệng.", List.of("Pizza cấp đông", "Tôm thẻ chân trắng"), List.of("Ớt chuông đủ màu", "Phô mai Mozzarella bào sợi")),
            new MealDef("Salad Cá Hồi Âu", "Tinh tế và dinh dưỡng.", List.of("Cá hồi phi lê", "Xà lách lolo xanh"), List.of("Bơ sáp", "Dầu ăn hướng dương", "Chanh không hạt")),
            new MealDef("Mì Ý Sốt Kem Nấm", "Béo ngậy thơm ngon.", List.of("Sợi mì Ý (Spaghetti)", "Nấm mỡ tươi"), List.of("Kem tươi (Whipping cream)", "Tiêu đen xay")),
            new MealDef("Steak Cá Hồi Bơ Chanh", "Dinh dưỡng tuyệt vời.", List.of("Cá hồi phi lê", "Bơ lạt tự nhiên"), List.of("Chanh không hạt", "Măng tây")),
            new MealDef("Salad Tôm Trái Bơ", "Thanh mát tốt cho sức khỏe.", List.of("Tôm thẻ chân trắng", "Bơ sáp"), List.of("Xà lách lolo xanh", "Cà chua beef")),
            new MealDef("Pizza Tôm Phô Mai", "Trẻ em cực thích.", List.of("Pizza cấp đông", "Tôm thẻ chân trắng"), List.of("Phô mai Mozzarella bào sợi")),
            new MealDef("Steak Bò Khoai Tây Chiên", "Kinh điển kiểu Pháp.", List.of("Thăn bò Úc", "Khoai tây Đà Lạt"), List.of("Bơ lạt tự nhiên", "Tiêu đen xay")),
            new MealDef("Cá Hồi Nướng Măng Tây Trưa", "Tốt cho tim mạch.", List.of("Cá hồi phi lê", "Măng tây"), List.of("Dầu ăn hướng dương")),
            new MealDef("Mì Ý Sốt Pesto Tôm", "Đậm đà hương vị thảo mộc.", List.of("Sợi mì Ý (Spaghetti)", "Tôm thẻ chân trắng", "Sốt Pesto"), List.of("Ớt chuông đủ màu", "Tiêu đen xay"))
        ));

        createBatch(products, "Trưa", "Eatclean", List.of(
            new MealDef("Ức Gà Áp Chảo Bông Cải", "Chuẩn Gym.", List.of("Đùi gà góc tư", "Bông cải xanh (Súp lơ)"), List.of("Ớt chuông đủ màu", "Dầu hào Maggi")),
            new MealDef("Poke Bowl Cá Hồi Trưa", "Trào lưu mới.", List.of("Cá hồi phi lê", "Gạo ST25"), List.of("Bơ sáp", "Dưa leo baby", "Rong biển ăn liền")),
            new MealDef("Bò Cuộn Măng Tây Trưa", "Dinh dưỡng cao.", List.of("Thăn bò Úc", "Măng tây"), List.of("Dầu ăn hướng dương", "Tiêu đen xay")),
            new MealDef("Salad Cá Hồi Bơ Sáp", "Chất béo tốt.", List.of("Cá hồi phi lê", "Bơ sáp"), List.of("Xà lách lolo xanh", "Hạt chia Úc")),
            new MealDef("Ức Gà Nướng Măng Tây", "Hỗ trợ giảm cân.", List.of("Đùi gà góc tư", "Măng tây"), List.of("Ớt chuông đủ màu", "Tiêu đen xay")),
            new MealDef("Cơm Lứt Gà Áp Chảo", "Đầy đủ chất xơ.", List.of("Đùi gà góc tư", "Gạo lứt"), List.of("Bông cải xanh (Súp lơ)", "Dầu ăn hướng dương")),
            new MealDef("Bò Xào Súp Lơ Xanh", "Protein và chất xơ dồi dào.", List.of("Thăn bò Úc", "Bông cải xanh (Súp lơ)"), List.of("Ớt chuông đủ màu", "Hành tây")),
            new MealDef("Tôm Hấp Bông Cải Xanh", "Ít calo, giàu protein.", List.of("Tôm thẻ chân trắng", "Bông cải xanh (Súp lơ)"), List.of("Tiêu đen xay")),
            new MealDef("Salad Ức Gà Sữa Chua", "Sốt sữa chua healthy.", List.of("Đùi gà góc tư"), List.of("Xà lách lolo xanh", "Sữa chua không đường")),
            new MealDef("Cơm Gạo Lứt Bò Cuộn", "Ngon lành dinh dưỡng.", List.of("Thăn bò Úc", "Gạo lứt"), List.of("Măng tây", "Tiêu đen xay"))
        ));

        // --- GROUP 3: BỮA TỐI (DINNER) ---
        createBatch(products, "Tối", "Truyền thống", List.of(
            new MealDef("Cá Kho Tộ Gia Đình", "Đậm đà vị quê.", List.of("Cá lóc đồng làm sẵn", "Ba rọi heo (Ba chỉ)"), List.of("Nước mắm cá cơm", "Tiêu đen xay", "Ớt hiểm trái")),
            new MealDef("Thịt Kho Tàu Trứng Tối", "Món ăn ấm cúng.", List.of("Ba rọi heo (Ba chỉ)", "Trứng gà ta (Hộp 10 quả)"), List.of("Nước mắm cá cơm", "Hành lá")),
            new MealDef("Canh Chua Cá Lóc Tối", "Thanh nhiệt mùa hè.", List.of("Cá lóc đồng làm sẵn", "Me cục nấu canh chua"), List.of("Cà chua beef", "Giá đỗ tươi", "Dứa (Thơm)")),
            new MealDef("Đùi Gà Chiên Nước Mắm", "Hấp dẫn cho trẻ nhỏ.", List.of("Đùi gà góc tư"), List.of("Nước mắm cá cơm", "Tương ớt Chinsu", "Hành lá")),
            new MealDef("Cá Thu Sốt Cà Chua", "Dinh dưỡng biển.", List.of("Cá thu cắt khúc", "Cà chua beef"), List.of("Hành lá", "Nước mắm cá cơm", "Tiêu đen xay")),
            new MealDef("Lươn Đồng Kho Sả Ớt", "Vị cay nồng.", List.of("Lươn đồng làm sạch", "Sả cây tươi"), List.of("Ớt hiểm trái", "Bột nghệ nguyên chất", "Nước mắm cá cơm")),
            new MealDef("Canh Cải Thịt Bằm Tối", "Thanh mát bữa tối.", List.of("Thịt heo xay", "Cải thìa"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Sườn Xào Chua Ngọt Tối", "Bữa cơm gia đình.", List.of("Sườn non heo", "Cà chua beef"), List.of("Hành tây", "Dứa (Thơm)")),
            new MealDef("Trứng Cuộn Hành Lá Tối", "Nhanh gọn, bổ dưỡng.", List.of("Trứng gà ta (Hộp 10 quả)", "Hành lá"), List.of("Dầu ăn hướng dương", "Tiêu đen xay")),
            new MealDef("Canh Chua Tôm Nam Bộ", "Vị chua ngọt đậm đà.", List.of("Tôm thẻ chân trắng", "Me cục nấu canh chua"), List.of("Cà chua beef", "Giá đỗ tươi")),
            new MealDef("Cá Lóc Kho Nghệ Tối", "Tốt cho tiêu hóa.", List.of("Cá lóc đồng làm sẵn", "Bột nghệ nguyên chất"), List.of("Ớt hiểm trái", "Hành lá")),
            new MealDef("Gà Kho Sả Ớt Tối", "Thơm nồng đưa cơm.", List.of("Đùi gà góc tư", "Sả cây tươi"), List.of("Ớt hiểm trái", "Hành lá")),
            new MealDef("Thịt Bò Xào Hành Tây Tối", "Bổ sung sắt dồi dào.", List.of("Thăn bò Úc", "Hành tây"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Canh Khoai Mỡ Thịt Bằm", "Món canh truyền thống.", List.of("Thịt heo xay", "Khoai mỡ"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Sườn Heo Kho Tiêu Tối", "Đậm đà đưa cơm.", List.of("Sườn non heo"), List.of("Hành lá", "Tiêu đen xay", "Ớt hiểm trái"))
        ));

        createBatch(products, "Tối", "Nhậu nhẹt", List.of(
            new MealDef("Mực Nướng Sa Tế Tối", "Mồi nhắm cực bén.", List.of("Mực ống tươi", "Tương ớt Chinsu"), List.of("Sả cây tươi", "Gừng già", "Dưa leo baby")),
            new MealDef("Bạch Tuộc Cay Tối", "Hấp dẫn vị giác.", List.of("Bạch tuộc mini", "Ớt hiểm trái"), List.of("Hành tây", "Cà chua beef")),
            new MealDef("Nghêu Hấp Sả Tối", "Ngọt vị biển.", List.of("Nghêu sạch", "Sả cây tươi"), List.of("Ớt hiểm trái", "Gừng già")),
            new MealDef("Ốc Hương Rang Muối", "Vị biển đậm đà.", List.of("Ốc hương"), List.of("Ớt hiểm trái", "Tiêu đen xay")),
            new MealDef("Hàu Sữa Nướng Mỡ Hành", "Bổ dưỡng.", List.of("Hàu sữa Pháp (Tách vỏ)", "Hành lá"), List.of("Đậu phộng rang", "Dầu mè thơm")),
            new MealDef("Tôm Hùm Hấp Bia Tối", "Đẳng cấp tiệc.", List.of("Tôm hùm Alaska", "Bia Heineken"), List.of("Chanh không hạt", "Muối i-ốt", "Ớt hiểm trái")),
            new MealDef("Mực Hấp Hành Gừng Tối", "Ngọt tự nhiên.", List.of("Mực ống tươi", "Hành lá"), List.of("Gừng già", "Ớt hiểm trái")),
            new MealDef("Bạch Tuộc Nướng Sa Tế Tối", "Dai giòn sần sật.", List.of("Bạch tuộc mini", "Tương ớt Chinsu"), List.of("Hành tây", "Dưa leo baby")),
            new MealDef("Nghêu Nướng Phô Mai Tối", "Béo ngậy đậm đà.", List.of("Nghêu sạch"), List.of("Phô mai Mozzarella bào sợi")),
            new MealDef("Ốc Hương Rang Muối Ớt", "Cay nồng quyến rũ.", List.of("Ốc hương"), List.of("Ớt hiểm trái", "Muối i-ốt"))
        ));

        createBatch(products, "Tối", "Chay", List.of(
            new MealDef("Lẩu Nấm Chay", "Thanh tịnh đêm tối.", List.of("Nấm đùi gà", "Đậu hũ non"), List.of("Cải thìa", "Bún tươi sấy khô")),
            new MealDef("Khổ Qua Kho Đậu Hũ", "Vị đắng nhẹ thanh nhiệt.", List.of("Khổ qua (Mướp đắng)", "Đậu hũ non"), List.of("Nước tương đậu nành", "Nấm mỡ tươi")),
            new MealDef("Đậu Hũ Tứ Xuyên Chay", "Cay nồng hấp dẫn.", List.of("Đậu hũ non", "Nấm mỡ tươi"), List.of("Cà chua beef", "Ớt hiểm trái", "Hành lá")),
            new MealDef("Canh Chua Chay Đậu Hũ", "Món canh thanh mát.", List.of("Đậu hũ non", "Me cục nấu canh chua"), List.of("Cà chua beef", "Giá đỗ tươi")),
            new MealDef("Nấm Kho Tiêu Chay", "Đậm đà hương vị chay.", List.of("Nấm đùi gà"), List.of("Nước tương đậu nành", "Tiêu đen xay")),
            new MealDef("Rau Củ Luộc Chấm Kho Quẹt", "Tươi ngon thanh đạm.", List.of("Bông cải xanh (Súp lơ)", "Khoai lang mật"), List.of("Nước tương đậu nành")),
            new MealDef("Đậu Hũ Sốt Cà Tomato", "Món ăn giản dị đưa cơm.", List.of("Đậu hũ non", "Cà chua beef"), List.of("Hành lá", "Tiêu đen xay")),
            new MealDef("Canh Nấm Đậu Hũ Chay", "Ngọt ngào thanh mát.", List.of("Nấm kim châm", "Đậu hũ non"), List.of("Hành lá", "Tiêu đen xay"))
        ));

        // --- GROUP 4: ĂN VẶT & ĐỒ UỐNG ---
        createBatch(products, "Tối", "Snack", List.of(
            new MealDef("Khoai Tây Chiên & Coke", "Combo giải trí.", List.of("Khoai tây Đà Lạt", "Coca-Cola (Lon 330ml)"), List.of("Tương ớt Chinsu", "Sốt Mayonnaise")),
            new MealDef("Xúc Xích Đức Nướng", "Nhanh gọn.", List.of("Xúc xích Đức"), List.of("Tương ớt Chinsu", "Dưa leo baby", "Sốt Mayonnaise")),
            new MealDef("Trứng Vịt Lộn Ngải Cứu", "Bổ dưỡng.", List.of("Trứng vịt lộn", "Ngải cứu tươi"), List.of("Gừng già", "Muối i-ốt")),
            new MealDef("Xúc Xích & Khoai Tây Chiên", "Món ăn vặt thơm ngon.", List.of("Xúc xích Đức", "Khoai tây Đà Lạt"), List.of("Tương ớt Chinsu", "Coca-Cola (Lon 330ml)"))
        ));

        log.info("Mass meal compilation complete. Total Meals: {}.", mealRepository.count());
    }

    private void createBatch(List<Product> products, String category, String goal, List<MealDef> definitions) {
        for (MealDef def : definitions) {
            createMealWithIngredients(products, def.name, def.desc, category, goal, def.primary, def.secondary);
        }
    }

    @Transactional
    public int normalizeAllMealIngredientsAtWritePath() {
        List<MealIngredient> ingredients = mealIngredientRepository.findAll();
        int updated = 0;
        for (MealIngredient ingredient : ingredients) {
            if (applyCanonicalAndQuantityParse(ingredient)) {
                mealIngredientRepository.save(ingredient);
                updated++;
            }
        }
        return updated;
    }

    private boolean applyCanonicalAndQuantityParse(MealIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        boolean changed = false;
        String sourceName = ingredient.getGenericName();
        if ((sourceName == null || sourceName.isBlank()) && ingredient.getProduct() != null) {
            sourceName = ingredient.getProduct().getName();
        }
        String normalized = ingredientTextNormalizer.normalize(sourceName);
        if (!normalized.isBlank() && ingredient.getCanonicalIngredient() == null) {
            Optional<IngredientAlias> alias = ingredientAliasRepository
                    .findFirstByAliasTextNormAndLangAndActiveTrue(normalized, "vi");
            if (alias.isPresent()) {
                ingredient.setCanonicalIngredient(alias.get().getCanonical());
                changed = true;
            }
        }

        String quantity = ingredient.getQuantity();
        if (quantity == null || quantity.isBlank()) {
            if (ingredient.getQuantityParseStatus() == null) {
                ingredient.setQuantityParseStatus("UNPARSED");
                ingredient.setQuantityParseConfidence(java.math.BigDecimal.ZERO);
                changed = true;
            }
            return changed;
        }
        QuantityParsingService.ParsedQuantity parsed = quantityParsingService.parse(quantity);
        if (parsed.value() != null && ingredient.getQuantityValue() == null) {
            ingredient.setQuantityValue(parsed.value());
            changed = true;
        }
        if (parsed.unitRaw() != null && (ingredient.getQuantityUnitRaw() == null || ingredient.getQuantityUnitRaw().isBlank())) {
            ingredient.setQuantityUnitRaw(parsed.unitRaw());
            changed = true;
        }
        if (parsed.unitCanonical() != null && ingredient.getQuantityUnitCanonical() == null) {
            ingredient.setQuantityUnitCanonical(parsed.unitCanonical());
            changed = true;
        }
        if (ingredient.getQuantityParseStatus() == null || "UNPARSED".equalsIgnoreCase(ingredient.getQuantityParseStatus())) {
            ingredient.setQuantityParseStatus(parsed.status().name());
            ingredient.setQuantityParseConfidence(parsed.confidence());
            changed = true;
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public List<CatalogMealOption> suggestMealsFromDb(String userMessage, java.util.Set<Long> excludedIds, int limit) {
        String lower = userMessage != null ? userMessage.toLowerCase() : "";
        String category = "Trưa"; // Default
        if (lower.contains("sang") || lower.contains("sáng")) category = "Sáng";
        else if (lower.contains("toi") || lower.contains("tối")) category = "Tối";
        else if (lower.contains("trua") || lower.contains("trưa")) category = "Trưa";

        List<Meal> meals = mealRepository.findByCategory(category);
        
        return meals.stream()
                .filter(m -> excludedIds == null || !excludedIds.contains(m.getId()))
                .limit(limit)
                .map(this::mapToCatalogOption)
                .collect(Collectors.toList());
    }

    private CatalogMealOption mapToCatalogOption(Meal meal) {
        List<MealIngredient> ingredients = mealIngredientRepository.findByMealId(meal.getId());
        
        List<String> allNames = ingredients.stream()
                .map(i -> i.getGenericName() != null ? i.getGenericName() : i.getProduct().getName())
                .toList();
        List<CatalogMealIngredient> details = ingredients.stream()
                .map(i -> new CatalogMealIngredient(
                        i.getGenericName() != null ? i.getGenericName() : i.getProduct().getName(), 
                        i.getProduct().getId(),
                        "PRIMARY".equals(i.getRole()) ? IngredientRole.PRIMARY : IngredientRole.SECONDARY))
                .collect(Collectors.toList());

        return new CatalogMealOption(
                meal.getId(),
                0,
                meal.getName(),
                allNames,
                details,
                meal.getDescription()
        );
    }


    private void createMealWithIngredients(List<Product> products, String mealName, String description, String category, String goal, List<String> primaryNames, List<String> secondaryNames) {
        Meal meal = Meal.builder()
                .name(mealName)
                .category(category)
                .dietaryGoal(goal)
                .description(description != null && !description.isBlank() ? description : "Món ăn được biên soạn từ kho sản phẩm thực tế.")
                .build();

        Meal savedMeal = mealRepository.save(meal);

        for (String name : primaryNames) {
            findProductByName(products, name).ifPresent(p -> {
                mealIngredientRepository.save(MealIngredient.builder()
                        .meal(savedMeal)
                        .product(p)
                        .genericName(name) // "Sườn heo", "Gạo", etc.
                        .role("PRIMARY")
                        .isMandatory(true)
                        .build());
            });
        }

        for (String name : secondaryNames) {
            findProductByName(products, name).ifPresent(p -> {
                mealIngredientRepository.save(MealIngredient.builder()
                        .meal(savedMeal)
                        .product(p)
                        .genericName(name)
                        .role("SECONDARY")
                        .isMandatory(false)
                        .build());
            });
        }
    }

    private java.util.Optional<Product> findProductByName(List<Product> products, String name) {
        String target = normalizeProductLookupText(name);
        if (target.isBlank()) {
            return java.util.Optional.empty();
        }

        java.util.Optional<Product> exact = products.stream()
                .filter(p -> normalizeProductLookupText(p.getName()).equals(target))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        return products.stream()
                .filter(p -> normalizeProductLookupText(p.getName()).contains(target))
                .min(java.util.Comparator.comparingInt(p -> normalizeProductLookupText(p.getName()).length()));
    }

    private String normalizeProductLookupText(String text) {
        if (text == null) return "";
        String value = text.toLowerCase(java.util.Locale.ROOT);
        value = value.replaceAll("(?iu)\\bphô\\s+mai\\b", " cheese ");
        value = value.replaceAll("(?iu)\\bpho\\s+mai\\b", " cheese ");
        value = value.replaceAll("(?iu)\\bphở\\b", " phonoodle ");
        value = value.replaceAll("(?iu)\\bpho\\b", " phonoodle ");
        String decomposed = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<Meal> findSuggestedMeals(String category, String goal) {
        return mealRepository.findByFilters(category, goal);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<CatalogMealOption> findMealByNameFuzzy(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return java.util.Optional.empty();
        }
        String normMsg = normalizeText(userMessage);
        java.util.Set<String> msgTokens = new java.util.HashSet<>(java.util.Arrays.asList(normMsg.split("\\s+")));
        if (msgTokens.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<Meal> allMeals = mealRepository.findAll();
        Meal bestMatch = null;
        double bestScore = 0.0;

        for (Meal meal : allMeals) {
            String normName = normalizeText(meal.getName());
            if (normName.isEmpty()) continue;
            
            if (normMsg.contains(normName) || normName.contains(normMsg)) {
                return java.util.Optional.of(mapToCatalogOption(meal));
            }

            java.util.Set<String> nameTokens = new java.util.HashSet<>(java.util.Arrays.asList(normName.split("\\s+")));
            long intersectionCount = nameTokens.stream()
                    .filter(msgTokens::contains)
                    .count();
            
            double score = (double) intersectionCount / nameTokens.size();
            if (intersectionCount >= 2 && score > bestScore) {
                bestScore = score;
                bestMatch = meal;
            }
        }

        if (bestScore >= 0.5) {
            return java.util.Optional.of(mapToCatalogOption(bestMatch));
        }
        return java.util.Optional.empty();
    }

    private static class MealDef {
        String name;
        String desc;
        List<String> primary;
        List<String> secondary;
        MealDef(String n, String d, List<String> p, List<String> s) { name=n; desc=d; primary=p; secondary=s; }
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
