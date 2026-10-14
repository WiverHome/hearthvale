"""
Подбор вариантов: для каждого спрайта набора - N картинок с seed 1..N в build/ai/candidates/<имя>/<seed>.png
и общий лист build/ai/candidates/<набор>.png с подписями seed. Лучший seed - в gen.py set ... --pick имя=seed.
  python tools/ai/candidates.py trees|res|rocks [--n 6] [--only tree_0,tree_0_snow]
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import gen


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("set", choices=["trees", "res", "rocks"])
    ap.add_argument("--n", type=int, default=6)
    ap.add_argument("--only", default="")
    a = ap.parse_args()
    only = set(filter(None, a.only.split(",")))
    jobs = []
    if a.set == "trees":
        for i, name in enumerate(gen.names("tree_")):
            jobs.append((name, f"game sprite of {gen.TREES[i % 6]}, full crown and trunk, plain white background"))
            jobs.append((name + "_snow", f"game sprite of {gen.WINTER[i % 6]}, full crown and trunk, plain white background"))
    elif a.set == "res":
        for rid, what in gen.RES.items():
            jobs.append(("res_" + rid, f"game inventory icon of {what}, single object centered, plain white background, bold simple shape"))
    else:
        for name in gen.names("rock_"):
            jobs.append((name, "isometric game sprite of a large grey mossy boulder rock formation, wide and low, plain white background"))
    jobs = [j for j in jobs if not only or j[0] in only]
    base = os.path.join(gen.DRAFTS, "candidates")
    rows = []
    for name, prompt in jobs:
        d = os.path.join(base, name)
        os.makedirs(d, exist_ok=True)
        tiles = []
        for seed in range(1, a.n + 1):
            img = gen.generate(prompt, seed=seed, isolated=True)
            img.save(os.path.join(d, f"{seed}.png"))
            t = img.resize((160, 160))
            ImageDraw.Draw(t).text((4, 2), f"{name} s{seed}", fill=(200, 0, 0))
            tiles.append(t)
        row = Image.new("RGB", (160 * len(tiles), 160), "white")
        for k, t in enumerate(tiles):
            row.paste(t, (160 * k, 0))
        rows.append(row)
        print("candidates", name)
    sheet = Image.new("RGB", (max(r.width for r in rows), 160 * len(rows)), "white")
    for k, r in enumerate(rows):
        sheet.paste(r, (0, 160 * k))
    sheet.save(os.path.join(base, a.set + ".png"))
    print("sheet", os.path.join(base, a.set + ".png"))


if __name__ == "__main__":
    main()
