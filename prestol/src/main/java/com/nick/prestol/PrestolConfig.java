package com.nick.prestol;

import com.nick.prestol.decree.DecreeType;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public final class PrestolConfig {
   public static final ForgeConfigSpec SPEC;
   public static final IntValue CHECK_INTERVAL;
   public static final BooleanValue SHOW_ACTIONBAR;
   public static final BooleanValue ANNOUNCE_GROWTH;
   public static final IntValue VILLAGE_CITIZENS;
   public static final IntValue VILLAGE_HALL;
   public static final IntValue VILLAGE_BUILDINGS;
   public static final IntValue TOWN_CITIZENS;
   public static final IntValue TOWN_HALL;
   public static final IntValue TOWN_BUILDINGS;
   public static final IntValue CAPITAL_CITIZENS;
   public static final IntValue BASE_SETTLEMENTS;
   public static final IntValue CITIZENS_PER_SETTLEMENT;
   public static final IntValue MAX_DISTANCE;
   public static final BooleanValue SAME_DIMENSION;
   public static final IntValue KINGDOM_POPULATION;
   public static final IntValue KINGDOM_TOWNS;
   public static final IntValue EMPIRE_POPULATION;
   public static final IntValue EMPIRE_TOWNS;
   public static final IntValue LAND_PER_SETTLEMENT;
   public static final IntValue LAND_PER_HALL_LEVEL;
   public static final IntValue LAND_PER_BUILDING;
   public static final IntValue LAND_REACH;
   public static final BooleanValue FLAGS_OVER_HALLS;
   public static final IntValue STANDARD_WOOL;
   public static final IntValue MOBILIZE_MINUTES;
   public static final IntValue SIEGE_RADIUS;
   public static final IntValue HOLD_SECONDS;
   public static final IntValue REVOLT_HOLD_SECONDS;
   public static final IntValue SIEGE_GRACE_SECONDS;
   public static final BooleanValue DEFENDER_ONLINE;
   public static final BooleanValue CAPITAL_LAST;
   public static final BooleanValue HOSTILE_RANK;
   public static final IntValue IMMUNITY_DAYS;
   public static final IntValue TRUCE_DAYS;
   public static final IntValue CHOICE_SECONDS;
   public static final IntValue LOOT_LOCK_MINUTES;
   public static final IntValue SLAVE_DEATH_PERCENT;
   public static final IntValue REVOLT_AFTER_DAYS;
   public static final DoubleValue REVOLT_HAPPINESS;
   public static final DoubleValue MUTINY_HAPPINESS;
   public static final IntValue MUTINY_DAYS;
   public static final BooleanValue OWNERS_DECREE;
   public static final IntValue CAPITAL_BONUS;
   public static final IntValue LAW_LOCK_MINUTES;
   public static final IntValue[] DURATION = new IntValue[DecreeType.values().length];
   public static final IntValue[] COOLDOWN = new IntValue[DecreeType.values().length];
   public static final ConfigValue<String> OFFLINE;
   public static final IntValue OFFLINE_CHUNKS;
   public static final IntValue OFFLINE_HOURS;
   public static final IntValue OFFLINE_COLONIES;
   public static final BooleanValue AI_FIXES;
   public static final BooleanValue SEVERAL_COLONIES;
   public static final BooleanValue ARMY_GARRISON;
   public static final IntValue ARMY_UNITS_PER_LEVEL;
   public static final IntValue ARMY_REFILL_SECONDS;
   public static final IntValue ARMY_FOOD;
   public static final IntValue ARMY_ARROWS;
   public static final BooleanValue MILITIA;
   public static final BooleanValue MERCENARIES;
   public static final IntValue MERCENARY_GOLD;
   public static final IntValue MERCENARY_MINUTES;
   public static final IntValue MERCENARY_HAMLET;
   public static final IntValue MERCENARY_VILLAGE;
   public static final IntValue MERCENARY_TOWN;
   public static final IntValue TRAINING_MINUTES;
   public static final IntValue MILITIA_CITIZENS;
   public static final IntValue MILITIA_MAX_HAMLET;
   public static final IntValue MILITIA_MAX_VILLAGE;
   public static final IntValue MILITIA_MAX_TOWN;
   public static final IntValue MILITIA_CAPITAL_PERCENT;
   public static final BooleanValue CONSCRIPTION;
   public static final BooleanValue CONSCRIPT_EMPLOYED;
   public static final BooleanValue PROVISIONS;
   public static final IntValue PROVISION_DAYS;
   public static final IntValue DAILY_RATION;
   public static final IntValue DESERT_DAYS;
   public static final BooleanValue NO_HYW_WORKERS;
   public static final BooleanValue MORALE;
   public static final IntValue ROUT_BELOW;
   public static final BooleanValue ECONOMY;
   public static final IntValue TAX_LOW;
   public static final IntValue TAX_NORMAL;
   public static final IntValue TAX_HIGH;
   public static final IntValue SOLDIER_PAY;
   public static final BooleanValue TRADE;
   public static final IntValue CARAVAN_COST;
   public static final IntValue PACK_ANIMAL_COST;
   public static final IntValue SHIP_COST;
   public static final IntValue MAX_TRADERS;
   public static final DoubleValue CARAVAN_SPEED;
   public static final DoubleValue SHIP_SPEED;
   public static final IntValue DWELL_SECONDS;
   public static final IntValue ROBBERY_CHANCE;
   public static final IntValue ESCORT_COST;

   private PrestolConfig() {
   }

   public static long durationTicks(DecreeType t) {
      return (long)((Integer)DURATION[t.ordinal()].get()).intValue() * 1200L;
   }

   public static long cooldownTicks(DecreeType t) {
      return (long)((Integer)COOLDOWN[t.ordinal()].get()).intValue() * 1200L;
   }

   static {
      Builder b = new Builder();
      b.push("general");
      CHECK_INTERVAL = b.comment("Как часто пересчитывать поселения, тиков. / How often settlements are recounted, ticks.")
         .defineInRange("checkIntervalTicks", 100, 20, 6000);
      SHOW_ACTIONBAR = b.comment("Показывать над хотбаром, чьи это земли, когда входишь в поселение. / Show whose land it is on entering a settlement.")
         .define("showActionBar", true);
      ANNOUNCE_GROWTH = b.comment("Объявлять всем, когда поселение выросло. / Announce to everybody when a settlement grows.").define("announceGrowth", true);
      b.pop();
      b.push("tiers");
      b.comment(
         new String[]{
            "Хутор — любое поселение. Село и город — по числу жителей, уровню ратуши и числу зданий.",
            "A hamlet is any settlement; village and town need citizens, a town hall level and buildings."
         }
      );
      VILLAGE_CITIZENS = b.defineInRange("village.minCitizens", 6, 0, 10000);
      VILLAGE_HALL = b.defineInRange("village.minTownHallLevel", 1, 0, 5);
      VILLAGE_BUILDINGS = b.defineInRange("village.minBuildings", 4, 0, 10000);
      TOWN_CITIZENS = b.defineInRange("town.minCitizens", 20, 0, 10000);
      TOWN_HALL = b.defineInRange("town.minTownHallLevel", 3, 0, 5);
      TOWN_BUILDINGS = b.defineInRange("town.minBuildings", 12, 0, 10000);
      CAPITAL_CITIZENS = b.comment("Столицей может быть только город, и в нём должно быть столько жителей. / A capital must be a town with this many citizens.")
         .defineInRange("capitalMinCitizens", 20, 0, 10000);
      b.pop();
      b.push("realm");
      BASE_SETTLEMENTS = b.comment("Сколько поселений может быть в державе. / Settlements a realm may hold.").defineInRange("baseMaxSettlements", 3, 1, 1000);
      CITIZENS_PER_SETTLEMENT = b.comment(
            "Плюс одно поселение за каждые столько жителей столицы (0 — без предела). / One more per this many capital citizens (0 = no limit)."
         )
         .defineInRange("capitalCitizensPerExtraSettlement", 10, 0, 10000);
      MAX_DISTANCE = b.comment(
            "Насколько далеко от столицы может быть поселение державы, блоков (0 — как угодно). / Max distance from the capital, blocks (0 = any)."
         )
         .defineInRange("maxDistanceFromCapital", 0, 0, 1000000);
      SAME_DIMENSION = b.comment("Поселения державы — только в одном мире со столицей. / Settlements only in the capital's dimension.")
         .define("requireSameDimension", true);
      KINGDOM_POPULATION = b.defineInRange("kingdom.minPopulation", 100, 0, 100000);
      KINGDOM_TOWNS = b.defineInRange("kingdom.minTowns", 2, 0, 1000);
      EMPIRE_POPULATION = b.defineInRange("empire.minPopulation", 300, 0, 100000);
      EMPIRE_TOWNS = b.defineInRange("empire.minTowns", 5, 0, 1000);
      SEVERAL_COLONIES = b.comment(
            new String[]{
               "Один игрок может владеть несколькими поселениями MineColonies: новая ратуша основывает ещё одно, старое остаётся за ним.",
               "One player may own several MineColonies colonies: a new town hall founds another, the old one stays his."
            }
         )
         .define("severalColoniesPerPlayer", true);
      b.pop();
      b.push("lands");
      b.comment(
         new String[]{
            "Сколько чанков держава может взять сама: за поселение, за уровень ратуши и за здание.",
            "How many chunks a realm may take by hand: per settlement, per town hall level and per building."
         }
      );
      LAND_PER_SETTLEMENT = b.defineInRange("perSettlement", 9, 0, 1000);
      LAND_PER_HALL_LEVEL = b.defineInRange("perTownHallLevel", 4, 0, 1000);
      LAND_PER_BUILDING = b.defineInRange("perBuilding", 1, 0, 1000);
      LAND_REACH = b.comment("Землю можно брать не дальше стольких чанков от ратуши. / Land is taken within this many chunks of the town hall.")
         .defineInRange("reachChunks", 48, 2, 512);
      b.pop();
      b.push("flags");
      FLAGS_OVER_HALLS = b.comment("Ставить мачту с флагом державы над ратушей каждого поселения. / Raise the realm's flag over every town hall.")
         .define("flagsOverTownHalls", true);
      STANDARD_WOOL = b.comment("Сколько шерсти (и одна палка) стоит знамя державы из книги. / Wool (plus a stick) a standard costs.")
         .defineInRange("standardWool", 2, 0, 64);
      b.pop();
      b.push("war");
      MOBILIZE_MINUTES = b.comment(
            "Сколько минут после объявления войны собираются войска; осаждать раньше нельзя. / Minutes between the declaration and the first siege."
         )
         .defineInRange("mobilizeMinutes", 10, 0, 1440);
      SIEGE_RADIUS = b.comment("Осада идёт в стольких блоках от ратуши. / Siege radius around the town hall, blocks.").defineInRange("siegeRadius", 16, 4, 128);
      HOLD_SECONDS = b.comment("Сколько секунд надо удержать ратушу, чтобы взять поселение. / Seconds the town hall must be held.")
         .defineInRange("holdSeconds", 600, 10, 86400);
      REVOLT_HOLD_SECONDS = b.comment("Сколько секунд держать ратушу при революции. / Seconds to hold it in a revolution.")
         .defineInRange("revoltHoldSeconds", 1200, 10, 86400);
      SIEGE_GRACE_SECONDS = b.comment(
            "Если у ратуши никого из осаждающих, осада снимается через столько секунд. / A siege with no attacker near ends after this many seconds."
         )
         .defineInRange("abandonAfterSeconds", 30, 1, 3600);
      DEFENDER_ONLINE = b.comment(
            "Осаду можно начать, только если хозяин или офицер поселения в игре. / A siege only starts while the owner or an officer is online."
         )
         .define("requireDefenderOnline", true);
      CAPITAL_LAST = b.comment("Столицу можно осадить, только когда у державы не осталось других поселений. / The capital falls last.")
         .define("capitalLast", true);
      HOSTILE_RANK = b.comment(
            "Во время войны игроки врага получают в ваших поселениях ранг «Враждебный» — стража их бьёт. / Enemy players are Hostile in your colonies during a war."
         )
         .define("hostileRankInWar", true);
      IMMUNITY_DAYS = b.comment("Взятое поселение нельзя взять снова столько игровых дней. / Game days a taken settlement cannot be taken again.")
         .defineInRange("captureImmunityDays", 3, 0, 1000);
      TRUCE_DAYS = b.comment("Перемирие после мира, игровых дней. / Truce after peace, game days.").defineInRange("truceDays", 1, 0, 1000);
      CHOICE_SECONDS = b.comment("Сколько секунд у победителя на выбор (потом — «оставить»). / Seconds the victor has to choose (then: keep).")
         .defineInRange("choiceSeconds", 120, 10, 3600);
      LOOT_LOCK_MINUTES = b.comment("Сколько минут сундуки с добычей открываются только победителям. / Minutes the loot chests open only for the victors.")
         .defineInRange("lootLockMinutes", 10, 0, 1440);
      SLAVE_DEATH_PERCENT = b.comment(
            "Сколько процентов порабощённых жителей умирает на каждом рассвете (не меньше одного). / Percent of enslaved citizens dying each dawn."
         )
         .defineInRange("enslavedDeathPercent", 10, 0, 100);
      REVOLT_AFTER_DAYS = b.comment(
            "Революцию можно поднять не раньше, чем через столько игровых дней после захвата. / Game days after the capture before a revolution."
         )
         .defineInRange("revoltAfterDays", 3, 0, 1000);
      REVOLT_HAPPINESS = b.comment("И только если счастье поселения ниже этого (0–10). / And only if happiness is below this (0-10).")
         .defineInRange("revoltBelowHappiness", 5.0, 0.0, 10.0);
      MUTINY_HAPPINESS = b.comment("Мятеж: счастье ниже этого (0–10)... / Mutiny: happiness below this (0-10)...")
         .defineInRange("mutinyBelowHappiness", 4.0, 0.0, 10.0);
      MUTINY_DAYS = b.comment(
            "...столько игровых дней подряд — и поселение выходит из державы (0 — мятежей нет). / ...for this many days in a row and it leaves the realm (0 = never)."
         )
         .defineInRange("mutinyDays", 3, 0, 1000);
      b.pop();
      b.push("decrees");
      OWNERS_DECREE = b.comment(
            "Владелец поселения может издавать указы для него (иначе только правитель). / Owners may issue decrees for their own settlement."
         )
         .define("ownersCanIssue", true);
      CAPITAL_BONUS = b.comment("В столице указы сильнее на столько процентов. / Decrees are this many percent stronger in the capital.")
         .defineInRange("capitalBonusPercent", 25, 0, 500);
      LAW_LOCK_MINUTES = b.comment("После отмены закона новые нельзя принять столько минут. / Minutes after a repeal before a new law.")
         .defineInRange("lawRepealLockMinutes", 20, 0, 1440);

      for (DecreeType t : DecreeType.values()) {
         DURATION[t.ordinal()] = b.defineInRange(t.id + ".durationMinutes", t.defaultDuration, 1, 600);
         COOLDOWN[t.ordinal()] = b.defineInRange(t.id + ".cooldownMinutes", t.defaultCooldown, 0, 1440);
      }

      b.pop();
      b.push("offline");
      OFFLINE = b.comment(
            new String[]{
               "Какие поселения державы работают, когда рядом никого нет и даже когда никого нет на сервере: off, capital, all.",
               "Which settlements keep working with nobody near or online: off, capital, all."
            }
         )
         .defineInList("settlements", "capital", List.of("off", "capital", "all"));
      OFFLINE_CHUNKS = b.comment("Сколько чанков поселения держать загруженными. / Chunks kept loaded per settlement.")
         .defineInRange("maxChunksPerSettlement", 121, 9, 1024);
      OFFLINE_HOURS = b.comment(
            "Через сколько часов без хозяина в игре поселение засыпает (0 — никогда). / Hours without an owner online before it sleeps (0 = never)."
         )
         .defineInRange("sleepAfterHours", 0, 0, 100000);
      OFFLINE_COLONIES = b.comment("Сколько поселений на сервере могут так работать одновременно. / How many settlements may work this way at once.")
         .defineInRange("maxSettlements", 12, 1, 500);
      b.pop();
      b.push("army");
      ARMY_GARRISON = b.comment(
            new String[]{
               "Сторожевые башни и башни казарм держат гарнизон юнитов «Столетней войны» вместо стражи MineColonies:",
               "жители стражниками больше не нанимаются. Нужен мод Hundred Years War.",
               "Guard towers and barracks towers keep a garrison of Hundred Years War soldiers instead of MineColonies guards."
            }
         )
         .define("garrisonInsteadOfGuards", true);
      ARMY_UNITS_PER_LEVEL = b.comment("Солдат гарнизона на каждый уровень здания. / Garrison soldiers per building level.")
         .defineInRange("unitsPerLevel", 1, 1, 10);
      ARMY_REFILL_SECONDS = b.comment("Через сколько секунд после потери приходит новый солдат (если на складе есть оружие и еда). / Seconds between recruits.")
         .defineInRange("recruitSeconds", 120, 5, 86400);
      ARMY_FOOD = b.comment("Сытость еды, которую берёт с собой каждый новый солдат (хлеб = 5). / Food (nutrition points) each recruit takes.")
         .defineInRange("foodPerRecruit", 10, 0, 200);
      ARMY_ARROWS = b.comment("Стрел на лучника или арбалетчика. / Arrows per archer or crossbowman.").defineInRange("arrowsPerRecruit", 16, 0, 256);
      MILITIA = b.comment(
            new String[]{
               "При осаде ратуши жители берут оружие: у ратуши встаёт ополчение. Погибший ополченец — погибший житель.",
               "When a town hall is besieged, its people take up arms; a fallen militiaman is a dead citizen."
            }
         )
         .define("militia", true);
      MILITIA_CITIZENS = b.comment("Один ополченец на столько взрослых жителей. / One militiaman per this many adult citizens.")
         .defineInRange("citizensPerMilitiaman", 4, 1, 100);
      MILITIA_MAX_HAMLET = b.comment("Больше всего ополченцев у хутора, села, города. / At most this many militiamen for a hamlet, village, town.")
         .defineInRange("maxHamlet", 3, 0, 100);
      MILITIA_MAX_VILLAGE = b.defineInRange("maxVillage", 6, 0, 100);
      MILITIA_MAX_TOWN = b.defineInRange("maxTown", 10, 0, 100);
      MILITIA_CAPITAL_PERCENT = b.comment("Столица собирает больше на столько процентов. / A capital raises this many percent more.")
         .defineInRange("capitalBonusPercent", 50, 0, 500);
      MERCENARIES = b.comment(
            new String[]{
               "Кнопка «Нанять наёмников» в ратуше нанимает отряд «Столетней войны» за золото: он ходит за нанявшим и служит срок.",
               "The town hall's \"hire mercenaries\" button hires a Hundred Years War squad for gold; it follows the hirer for a while."
            }
         )
         .define("mercenaries", true);
      MERCENARY_GOLD = b.comment("Золотых слитков за одного наёмника. / Gold ingots per mercenary.").defineInRange("mercenaryGoldEach", 2, 0, 640);
      MERCENARY_MINUTES = b.comment("Сколько минут служат наёмники (20 минут — игровой день). / Minutes of service (20 = one game day).")
         .defineInRange("mercenaryServiceMinutes", 20, 1, 10000);
      MERCENARY_HAMLET = b.comment("Наёмников в отряде у хутора, села, города. / Squad size for a hamlet, village, town.")
         .defineInRange("mercenariesHamlet", 3, 1, 50);
      MERCENARY_VILLAGE = b.defineInRange("mercenariesVillage", 4, 1, 50);
      MERCENARY_TOWN = b.defineInRange("mercenariesTown", 6, 1, 50);
      TRAINING_MINUTES = b.comment(
            new String[]{
               "Раз в столько минут стрельбище учит стрелков, а боевая академия — пехоту гарнизонов поселения: +1 ранг до предела",
               "(уровень башни + 2 за уровень училища). / Every this many minutes the archery trains the settlement's garrison",
               "archers and the combat academy its foot: +1 rank up to the tower level + 2 per school level."
            }
         )
         .defineInRange("trainingMinutes", 10, 1, 10000);
      CONSCRIPTION = b.comment(
            new String[]{
               "Солдат гарнизона — житель поселения: он уходит из колонии в башню, а при роспуске возвращается со своим именем и навыками.",
               "A garrison soldier is one of the settlement's citizens: he leaves the colony for the tower and comes back when discharged."
            }
         )
         .define("conscription", true);
      CONSCRIPT_EMPLOYED = b.comment("Если безработных нет, призывать и работающих жителей. / Draft working citizens when nobody is idle.")
         .define("conscriptEmployed", false);
      PROVISIONS = b.comment(
            new String[]{
               "Провиант: солдаты игроков едят обычную еду из колонии (склад, ратуша, своя башня), с пунктов снабжения HYW или из сумки командира.",
               "Снабжение «Столетней войны» при этом выключается. / Soldiers eat colony food instead of Hundred Years War supply."
            }
         )
         .define("provisions", true);
      PROVISION_DAYS = b.comment("На сколько игровых дней хватает полного запаса провианта. / Game days a full provisions bar lasts.")
         .defineInRange("provisionDays", 2, 1, 30);
      DAILY_RATION = b.comment("Сытость еды, которую солдат съедает за игровой день (хлеб = 5). / Food points a soldier eats per game day.")
         .defineInRange("dailyRation", 6, 1, 100);
      DESERT_DAYS = b.comment("Через сколько игровых дней без еды солдат дезертирует (0 — никогда). / Starving days before a soldier deserts (0 = never).")
         .defineInRange("desertAfterDays", 2, 0, 100);
      MORALE = b.comment(
            new String[]{
               "Боевой дух солдат игроков: сытость, жалованье, своя земля, командир рядом, опыт и перевес в бою; гибель товарищей его роняет.",
               "Высокий дух бьёт сильнее, низкий слабее, сломленный солдат бежит. / Soldier morale: spirit drives damage and routs."
            }
         )
         .define("morale", true);
      ROUT_BELOW = b.comment("Ниже какого духа солдат бежит с поля боя (0 — никогда). / Morale below which a soldier flees (0 = never).")
         .defineInRange("routBelow", 20, 0, 100);
      NO_HYW_WORKERS = b.comment(
            "Вырезать рабочих «Столетней войны»: их нельзя нанять, их свитки и верстаки не работают. / Remove Hundred Years War workers."
         )
         .define("removeHywWorkers", true);
      b.pop();
      b.push("economy");
      ECONOMY = b.comment("Казна держав: налоги с поселений, жалованье гарнизонам, монеты из золота. / Realm treasuries: taxes, soldier pay, gold coins.")
         .define("enabled", true);
      TAX_LOW = b.comment(
            "Монет с каждого жителя в игровой день при низком, обычном и высоком налоге (самородок = 1, слиток = 9). / Coins per citizen per game day."
         )
         .defineInRange("taxLow", 1, 0, 1000);
      TAX_NORMAL = b.defineInRange("taxNormal", 2, 0, 1000);
      TAX_HIGH = b.defineInRange("taxHigh", 3, 0, 1000);
      SOLDIER_PAY = b.comment("Жалованье солдату гарнизона, монет в игровой день. / Pay of a garrison soldier, coins per game day.")
         .defineInRange("soldierPay", 2, 0, 1000);
      b.pop();
      b.push("trade");
      TRADE = b.comment("Караваны и корабли, рынки поселений, торговля между державами. / Caravans, ships, settlement markets, trade between realms.")
         .define("enabled", true);
      CARAVAN_COST = b.comment("Сколько монет стоит снарядить караван (без вьючных животных). / Coins to fit out a caravan.")
         .defineInRange("caravanCost", 96, 0, 100000);
      PACK_ANIMAL_COST = b.comment("Цена одного вьючного мула (до 4 в караване). / Coins per pack mule (up to 4).").defineInRange("packAnimalCost", 48, 0, 100000);
      SHIP_COST = b.comment("Сколько монет стоит торговый корабль. / Coins for a merchant ship.").defineInRange("shipCost", 192, 0, 100000);
      MAX_TRADERS = b.comment("Сколько караванов и кораблей может быть у одной державы. / Traders per realm.").defineInRange("maxPerRealm", 6, 0, 100);
      CARAVAN_SPEED = b.comment("Скорость каравана, блоков в секунду. / Caravan speed, blocks per second.").defineInRange("caravanSpeed", 2.5, 0.5, 20.0);
      SHIP_SPEED = b.comment("Скорость корабля, блоков в секунду. / Ship speed, blocks per second.").defineInRange("shipSpeed", 6.0, 0.5, 30.0);
      DWELL_SECONDS = b.comment("Сколько секунд торговец стоит в каждом поселении. / Seconds a trader stays at each stop.")
         .defineInRange("dwellSeconds", 30, 1, 3600);
      ROBBERY_CHANCE = b.comment(
            "Шанс (%), что караван без охраны ограбят разбойники на перегоне по чужой или ничьей земле. / Robbery chance per leg without escort."
         )
         .defineInRange("robberyChance", 3, 0, 100);
      ESCORT_COST = b.comment("Монет за одного стражника охраны на перегон. / Coins per escort guard per leg.").defineInRange("escortCost", 4, 0, 10000);
      b.pop();
      b.push("workers");
      AI_FIXES = b.comment("Поправки ИИ жителей: инструмент в руках, лесоруб без лишней ходьбы, еда без таверны. / Citizen AI fixes.").define("aiFixes", true);
      b.pop();
      SPEC = b.build();
   }
}
