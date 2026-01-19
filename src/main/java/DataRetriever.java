import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever {
    private final DBConnection dbConnection = new DBConnection();

    public Dish findDishById(Integer id) {
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            String sql = """
                SELECT d.id, d.name, d.dish_type, d.price,
                       i.id AS ing_id, i.name AS ing_name, i.category AS ing_category, i.price AS ing_price,
                       di.quantity_required, di.unit
                FROM dish d
                LEFT JOIN DishIngredient di ON d.id = di.id_dish
                LEFT JOIN ingredient i ON di.id_ingredient = i.id
                WHERE d.id = ?
                ORDER BY di.id;
                """;
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            Dish dish = null;
            List<Ingredient> ingredients = new ArrayList<>();
            while (rs.next()) {
                if (dish == null) {
                    dish = new Dish();
                    dish.setId(rs.getInt("id"));
                    dish.setName(rs.getString("name"));
                    dish.setDishType(DishTypeEnum.valueOf(rs.getString("dish_type").toUpperCase()));
                    Double price = rs.getDouble("price");
                    dish.setPrice(rs.wasNull() ? null : price);
                }
                if (rs.getObject("ing_id") != null) {
                    Ingredient ing = new Ingredient(rs.getInt("ing_id"));
                    ing.setName(rs.getString("ing_name"));
                    ing.setCategory(CategoryEnum.valueOf(rs.getString("ing_category")));
                    Double ingPrice = rs.getDouble("ing_price");
                    ing.setPrice(ingPrice);
                    Double quantity = rs.getDouble("quantity_required");
                    ing.setQuantity(rs.wasNull() ? null : quantity);
                    ingredients.add(ing);
                }
            }
            if (dish != null) {
                dish.setIngredients(ingredients); 
            }
            dbConnection.closeConnection(conn);
            if (dish == null) {
                throw new RuntimeException("Dish not found: " + id);
            }
            return dish;
        } catch (SQLException e) {
            dbConnection.closeConnection(conn);
            throw new RuntimeException("Erreur lors de la récupération du plat ID: " + id, e);
        }
    }

    public Dish saveDish(Dish toSave) {
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);

            Integer dishId;
            if (toSave.getId() == null) {
                String insertDishSql = """
                    INSERT INTO dish (name, dish_type, price) 
                    VALUES (?, ?::dish_type, ?) 
                    RETURNING id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(insertDishSql)) {
                    ps.setString(1, toSave.getName());
                    ps.setString(2, toSave.getDishType().name());
                    if (toSave.getPrice() != null) {
                        ps.setDouble(3, toSave.getPrice());
                    } else {
                        ps.setNull(3, Types.NUMERIC);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dishId = rs.getInt("id");
                            toSave.setId(dishId);
                        } else {
                            throw new RuntimeException("INSERT dish failed");
                        }
                    }
                }
            } else {
                String updateDishSql = """
                    UPDATE dish 
                    SET name = ?, dish_type = ?::dish_type, price = ? 
                    WHERE id = ? 
                    RETURNING id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(updateDishSql)) {
                    ps.setString(1, toSave.getName());
                    ps.setString(2, toSave.getDishType().name());
                    if (toSave.getPrice() != null) {
                        ps.setDouble(3, toSave.getPrice());
                    } else {
                        ps.setNull(3, Types.NUMERIC);
                    }
                    ps.setInt(4, toSave.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dishId = rs.getInt("id");
                        } else {
                            throw new RuntimeException("UPDATE dish failed: no rows affected");
                        }
                    }
                }
            }

            detachDishIngredients(conn, dishId); 
            List<Ingredient> ingredients = toSave.getIngredients();
            if (ingredients != null && !ingredients.isEmpty()) {
                attachDishIngredients(conn, dishId, ingredients);
            }

            conn.commit();
            return findDishById(dishId);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    throw new RuntimeException("Rollback échoué", ex);
                }
            }
            throw new RuntimeException("Erreur lors de la sauvegarde du plat: " + toSave.getName(), e);
        } finally {
            dbConnection.closeConnection(conn);
        }
    }

    public List<Ingredient> createIngredients(List<Ingredient> newIngredients) {
        if (newIngredients == null || newIngredients.isEmpty()) {
            return List.of();
        }
        List<Ingredient> savedIngredients = new ArrayList<>();
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            String insertSql = """
                INSERT INTO ingredient (name, category, price) 
                VALUES (?, ?::ingredient_category, ?) 
                RETURNING id
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Ingredient ingredient : newIngredients) {
                    if (ingredient.getId() != null) {
                        throw new RuntimeException("ID ne doit pas être set pour création: " + ingredient.getName());
                    }
                    ps.setString(1, ingredient.getName());
                    ps.setString(2, ingredient.getCategory().name());
                    ps.setDouble(3, ingredient.getPrice());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int generatedId = rs.getInt("id");
                            ingredient.setId(generatedId);
                            savedIngredients.add(ingredient);
                        }
                    }
                }
                conn.commit();
                return savedIngredients;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Erreur lors de la création des ingrédients", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur de connexion", e);
        } finally {
            dbConnection.closeConnection(conn);
        }
    }

    private void detachDishIngredients(Connection conn, Integer dishId) throws SQLException {
        String deleteSql = "DELETE FROM DishIngredient WHERE id_dish = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, dishId);
            ps.executeUpdate();
        }
    }

    private void attachDishIngredients(Connection conn, Integer dishId, List<Ingredient> ingredients) throws SQLException {
        if (ingredients.isEmpty()) return;
        String insertSql = """
            INSERT INTO DishIngredient (id_dish, id_ingredient, quantity_required, unit) 
            VALUES (?, ?, ?, ?::unit_type)
            """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (Ingredient ing : ingredients) {
                if (ing.getId() == null) {
                    throw new RuntimeException("ID ingrédient manquant pour jointure: " + ing.getName());
                }
                ps.setInt(1, dishId);
                ps.setInt(2, ing.getId());
                ps.setDouble(3, ing.getQuantity() != null ? ing.getQuantity() : 0.0);
                ps.setString(4, "KG"); 
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<Ingredient> findIngredientByDishId(Integer idDish) {
        throw new UnsupportedOperationException("Utilise findDishById pour charger ingrédients avec quantités.");
    }

}