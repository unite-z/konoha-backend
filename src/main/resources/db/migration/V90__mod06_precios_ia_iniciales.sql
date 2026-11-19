-- AV-396: precios oficiales consultados el 12/11/2026, en USD por millón de tokens.
-- DeepSeek (api-docs.deepseek.com/quick_start/pricing): precio de hora punta; fuera de ella cobra la mitad,
-- así que el coste calculado es el máximo. Gemini (aistudio.google.com/docs/pricing, Paid Tier, Standard):
-- la salida incluye los tokens de razonamiento y el precio se duplica desde el 01/01/2027.
-- gemini-3.7-flash y gemini-3.5-flash no aparecen en la página de precios: sin precio.
INSERT INTO mod06_precio_ia (modelo, vigente_desde, usd_millon_entrada, usd_millon_salida, usd_millon_cache) VALUES
    ('deepseek-flash', DATE '2026-01-01', 0.30, 1.20, 0.006),
    ('deepseek-v4-pro', DATE '2026-01-01', 1.32, 3.96, 0.044),
    ('gemini-3.8-flash', DATE '2026-01-01', 0.75, 3.75, 0.075),
    ('gemini-3.8-flash', DATE '2027-01-01', 1.50, 7.50, 0.15),
    ('gemini-3.6-flash', DATE '2026-01-01', 0.75, 3.75, 0.075),
    ('gemini-3.6-flash', DATE '2027-01-01', 1.50, 7.50, 0.15)
ON CONFLICT (modelo, vigente_desde) DO NOTHING;
