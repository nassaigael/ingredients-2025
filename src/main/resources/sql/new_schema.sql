
CREATE TYPE unit_type AS ENUM ('PCS', 'KG', 'L');

CREATE TABLE IF NOT EXISTS DishIngredient (
    id SERIAL PRIMARY KEY,
    id_dish INT REFERENCES dish(id) ON DELETE CASCADE,
    id_ingredient INT REFERENCES ingredient(id) ON DELETE CASCADE,
    quantity_required NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    unit unit_type NOT NULL DEFAULT 'KG',
    UNIQUE(id_dish, id_ingredient) 
);


INSERT INTO DishIngredient (id_dish, id_ingredient, quantity_required, unit)
SELECT id_dish, id, required_quantity, 'KG' AS unit
FROM ingredient
WHERE id_dish IS NOT NULL AND required_quantity IS NOT NULL;

ALTER TABLE ingredient DROP COLUMN IF EXISTS id_dish;
ALTER TABLE ingredient DROP COLUMN IF EXISTS required_quantity;


INSERT INTO DishIngredient (id_dish, id_ingredient, quantity_required, unit)
VALUES 
    (1, 1, 0.20, 'KG'),  
    (1, 2, 0.15, 'KG'),  
    (2, 3, 1.00, 'KG'), 
    (4, 4, 0.30, 'KG'),  
    (4, 5, 0.20, 'KG'); 

UPDATE dish SET price = 3500.00 WHERE id = 1;  
UPDATE dish SET price = 12000.00 WHERE id = 2; 
UPDATE dish SET price = NULL WHERE id = 3;     
UPDATE dish SET price = 8000.00 WHERE id = 4;  
UPDATE dish SET price = NULL WHERE id = 5;   

CREATE INDEX IF NOT EXISTS idx_dishingredient_dish ON DishIngredient(id_dish);
CREATE INDEX IF NOT EXISTS idx_dishingredient_ingredient ON DishIngredient(id_ingredient);
