import json

with open('knowledge-base/pesticide-labels/cibrc_approved_pesticides_kb07_kb08.json', 'r', encoding='utf-8') as f:
    items = json.load(f)

print(f"Total Records: {len(items)}\n")
for i, item in enumerate(items):
    pest = item.get('target_disease') or item.get('target_pest')
    ai = item.get('active_ingredient')
    trade = item.get('product_trade_name')
    crop = item.get('target_crop')
    method = item.get('application_method')
    method_cat = item.get('method_category')
    d_ha = item.get('dosage_per_hectare')
    d_ac = item.get('dosage_per_acre')
    water = item.get('dilution_water_liters')
    phi = item.get('pre_harvest_interval_days')
    reg = item.get('cibrc_registration_number')
    print(f"[{i+1:02d}] Crop: {crop} | Target: {pest} | AI: {ai} | Trade: {trade}")
    print(f"     Method: {method} ({method_cat}) | Dose/ha: {d_ha} | Dose/ac: {d_ac} | Water: {water} | PHI: {phi}d | Reg: {reg}")
