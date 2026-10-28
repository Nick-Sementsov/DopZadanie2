# Престол

Державы, войны, осады и армия для MineColonies, в связке с Hundred Years War (сборка Kingmine).

Forge 1.20.1, Java 17. Исходники восстановлены из `prestol-1.0.0.jar` и переведены на официальные имена Mojang.

## Сборка

1. Положите свою сборку HYW в `libs/HundredYearsWar-0.7.1r-1.20.1-forge-extras.jar`
   (имя должно совпадать с `hyw_jar_version` в `gradle.properties`; jar в git не попадает).
2. Соберите:

```bash
./gradlew build
```

Готовый мод: `build/libs/prestol-<версия>.jar`.

## Зависимости

| Мод | Как подключён | Где берётся |
|---|---|---|
| MineColonies, BlockUI, Structurize | обязательные | maven LDT Team |
| Hundred Years War | необязательная (армия, наёмники, ополчение) | `libs/` |
| Xaero's World Map / Minimap | необязательные, только клиент | Modrinth maven |
| Immersive Paintings, Betsy Ross | необязательные (флаги-картинки) | Modrinth maven |
| EcoMinecolonies | необязательная (бригады строителей) | заглушки в `src/stubs`, в jar не попадают |
| [Let's Do] Brewery, Vinery | необязательные (хижины пивовара и винодела) | ничего не нужно при сборке |

## Пивовар и винодел (Let's Do)

Если на сервере стоит Let's Do Brewery и/или Vinery, появляются хижины MineColonies «Хижина пивовара» и «Хижина винодела»
(3 уровня, пакеты «Prestol: Brewery» и «Prestol: Winery» в инструменте строительства). Рабочий сам сушит зерно, варит пиво
и виски, давит виноград и ставит вина; дорогие напитки открываются с уровнем хижины.

Чертежи, рецепты рабочих и картинки пакетов сделаны генератором из установленных модов:

```bash
python3 -I tools/gen_letsdo.py <minecolonies.jar> <letsdo-brewery.jar> <letsdo-vinery.jar>
```

Он пишет `src/main/resources/blueprints/prestol/{brewery,winery}` (чертежи взяты из стиля MineColonies «Medieval Oak»,
GPL-3.0, с заменой блока хижины) и `src/main/resources/packs/letsdo_{brewery,vinery}` (встроенные датапаки с рецептами,
включаются только при установленном моде). Свои постройки можно отсканировать Scan Tool и положить на место чертежей.

## Структура

- `com.nick.prestol.realm` — державы, поселения, ступени
- `com.nick.prestol.war` — войны, осады, захват, революции
- `com.nick.prestol.army` — гарнизоны, ополчение, наёмники, снабжение
- `com.nick.prestol.decree` — указы и законы
- `com.nick.prestol.land` — земли и территория
- `com.nick.prestol.flag` — флаги и мачты
- `com.nick.prestol.map`, `client` — карта и «Книга державы»
- `com.nick.prestol.work` — фиксы ИИ жителей, офлайн-работа
- `com.nick.prestol.colony` — хижины пивовара и винодела
- `com.nick.prestol.mixin` — миксины в MineColonies, Xaero и Hundred Years War
