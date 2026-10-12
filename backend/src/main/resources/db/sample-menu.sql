INSERT INTO public.menu_items
    (name, description, price, category, available)
SELECT name, description, price, category, available
FROM (
    VALUES
        ('Cappuccino', 'Espresso with steamed milk and foam',
         3.50, 'Coffee', true),
        ('Käsekuchen', 'Creamy cheesecake',
         4.20, 'Cake', true),
        ('Buttercroissant', 'Freshly baked butter croissant',
         2.50, 'Bakery', true)
) AS sample(name, description, price, category, available)
WHERE NOT EXISTS (
    SELECT 1 FROM public.menu_items
);

SELECT id, name, description, price, category, available
FROM public.menu_items
ORDER BY id;