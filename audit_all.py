import json

with open('knowledge-base/pesticide-labels/cibrc_approved_pesticides_kb07_kb08.json', 'r', encoding='utf-8') as f:
    records = json.load(f)

print(f"Loaded {len(records)} records.\n")

for i, r in enumerate(records):
    print(f"[{i+1}] {r.get('product_trade_name')} ({r.get('active_ingredient')})")
    print(f"    Crop: {r.get('target_crop')}, Pest/Dis: {r.get('target_pest') or r.get('target_disease') or r.get('target_weed')}")
    print(f"    Dose/ha: {r.get('dosage_per_hectare')}, Dose/acre: {r.get('dosage_per_acre')}")
    print(f"    Water: {r.get('dilution_water_liters')}")
    print(f"    Method: {r.get('application_method')}, Category: {r.get('method_category')}")
    print(f"    Knapsack: {r.get('dosage_per_15l_knapsack')}")
    print(f"    PHI: {r.get('pre_harvest_interval_days')}, Max Apps: {r.get('max_applications_per_season')}")
    print(f"    Re-entry in record: {r.get('re_entry_interval')} | Toxicity: {r.get('toxicity_band')}")
    print()
