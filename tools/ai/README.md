# SDXL Turbo: как самому делать картинки для Hearthvale

Генератор работает локально на видеокарте (хватает 6 ГБ памяти). Интернет нужен только при первом запуске: модель (~7 ГБ) и модель вырезания фона (~170 МБ) скачиваются в кэш `C:\Users\<вы>\.cache\huggingface` и `~\.u2net`.

## Что где лежит

- `<папка вне проекта>/sdxl/.venv` - Python-окружение (PyTorch с CUDA, diffusers, rembg). Вне репозитория.
- `tools/ai/gen.py` - скрипт генерации (в репозитории).
- `build/ai/<набор>/` - черновики (полные картинки до обрезки), не в git.
- `tools/art/override/<имя спрайта>.png` - замены спрайтов, их подхватывает генератор атласа.
- `tools/art/title.jpg` - картина титульного экрана.

Удобно завести короткую команду (Git Bash, из корня проекта):

```bash
alias sd='<папка>/sdxl/.venv/Scripts/python tools/ai/gen.py'
```

## 1. Просто картинка по запросу

```bash
sd free "a cozy medieval tavern interior" --out build/ai/tavern.png
```

Параметры:
- `--w 768 --h 512` - размер (кратно 64; модель учена на 512x512, больше 1024 - хуже).
- `--seed 5` - номер «случайности»: тот же seed и запрос дают ту же картинку. Понравился вариант - запомните seed.
- `--n 8` - сразу 8 вариантов (seed, seed+1, ...), файлы `..._<seed>.png`. Самый быстрый способ найти удачный.
- `--steps 2` - шаги (1-4). 1 - быстрее и грубее, 4 - чуть детальнее.
- `--nobg` - убрать фон, прозрачный PNG (для предметов и значков; в запросе пишите `plain white background`).
- `--tile` - бесшовная текстура (края стыкуются сами с собой) - для земли, стен, тканей.

К запросу автоматически добавляется общий стиль игры (рисованная графика, мягкие градиенты, свет сверху слева) и «чего не надо» (текст, водяные знаки, рамки, люди). Поменять - константы `STYLE` и `NEG` в начале `gen.py`.

Советы по запросам (пишите по-английски):
- один предмет: `game icon of ..., single object centered, plain white background, bold simple shape`;
- спрайт для карты: `isometric game sprite of ..., plain white background`;
- текстура: `seamless texture of ... seen from above, flat even lighting` и `--tile`;
- меньше слов - предсказуемее. Важное - в начале.

## 2. Готовые наборы для игры

```bash
sd set res            # значки ресурсов res_* (доски, хлеб, рыба...)
sd set trees          # деревья tree_* и их зимние версии
sd set rocks          # скалы rock_*
sd set bushes         # кусты prop_bush_* и заснеженные
sd set ground         # клетки земли: луг, снег, лес, дорога, камень (вода остаётся своя - у неё блики-кадры)
sd set title          # картина титульного экрана tools/art/title.jpg
sd set icon           # иконка приложения tools/art/override/icon.png
```

Набор сам вписывает картинку в размер и точку привязки спрайта (берёт их из `assets/sprites/atlas.json`), вырезает фон, добавляет тень и тёмную обводку и пишет в `tools/art/override/`.

Не понравилось:
- другой seed для всего набора: `sd set trees --seed 21`;
- только часть: `sd set res --only bread,fish --seed 3`, `sd set trees --only tree_2`;
- поправить запрос: словари `RES`, `TREES`, `GROUND` и тексты в функциях `set_*` в `gen.py`.

## 3. Собрать игру с новыми картинками

```bash
./gradlew :tools:run        # атлас assets/sprites/atlas*.png, иконки Android и веба
./gradlew :desktop:run      # посмотреть на ПК
./gradlew :desktop:shots    # кадры интерфейса в build/shots
```

Вернуть нарисованное кодом: удалить файл из `tools/art/override/` (или `tools/art/title.jpg`) и снова `./gradlew :tools:run`.

Ручная замена тоже работает: положите свой PNG (из любого генератора или редактора) в `tools/art/override/<имя спрайта>.png`. Имена спрайтов - ключи в `assets/sprites/atlas.json`. Лучше сохранять пропорции исходного спрайта: точка привязки масштабируется вместе с картинкой.

Здания и жителей лучше не заменять: они из Blender с 4 сторон и в зимнем виде, а генератор не нарисует один и тот же дом с четырёх сторон.

## Лицензия

SDXL Turbo - Stability AI Community License (https://huggingface.co/stabilityai/sdxl-turbo/blob/main/LICENSE.md), Powered by Stability AI. Созданные картинки принадлежат проекту (в игре - CC BY-NC 4.0); модель в репозиторий и игру не входит. Условия: бесплатно для некоммерческого использования и для проектов с доходом меньше 1 млн $ в год (Hearthvale бесплатный). Каждый набор, попавший в игру, записывайте в `assets/CREDITS.md`: что, чем и когда сделано.

## Если что-то не так

- `CUDA out of memory` - закройте игры и браузер с тяжёлыми вкладками; уменьшите `--w/--h`.
- Первый запуск долгий (скачивание модели), дальше ~2-5 с на картинку 512x512.
- Пересоздать окружение:
  ```bash
  cd <папка>/sdxl && python -m venv .venv
  .venv/Scripts/python -m pip install torch --index-url https://download.pytorch.org/whl/cu128
  .venv/Scripts/python -m pip install diffusers transformers accelerate safetensors "rembg[gpu]" pillow numpy
  ```
