# Моды для Minecraft Forge 1.20.1

В репозитории два независимых мода. Каждый лежит в своей папке, собирается в свой `.jar`,
и их можно ставить по отдельности или вместе.

| Мод | Папка | Что это |
|-----|-------|---------|
| [Шахматы](chess/README.md) | `chess/` | Шахматная доска с объёмными фигурами, полные правила, компьютерный соперник, партии двух игроков, шесть стилей |
| [Рыцарский бой](knightcombat/README.md) | `knightcombat/` | Ближний бой в духе Chivalry 2: удары сбоку, сверху и уколы с замахом, блок и парирование, рипост, финты, пинок, выносливость; оружие Minecraft, Epic Knights и его аддонов |

| Шахматы | Рыцарский бой |
|---------|---------------|
| ![Шахматная доска](chess/docs/screenshots/board.png) | ![Противник замахивается цвайхендером Epic Knights](knightcombat/docs/screenshots/enemy_windup.png) |

## Сборка

Нужен **JDK 17** (если его нет, Gradle скачает сам). Первая сборка занимает несколько минут:
Gradle скачивает и декомпилирует Minecraft.

| Команда | Что делает |
|---------|-----------|
| `./gradlew build` | Собирает оба мода и запускает тесты |
| `./gradlew :chess:build` | Только шахматы → `chess/build/libs/chessmod-1.2.0.jar` |
| `./gradlew :knightcombat:build` | Только рыцарский бой → `knightcombat/build/libs/knightcombat-2.1.0.jar` |
| `./gradlew :chess:runClient` | Minecraft с модом шахмат (`:knightcombat:runClient` — с боевым модом) |
| `./gradlew :chess:runServer` | Выделенный сервер с модом (`:knightcombat:runServer`) |

На Windows вместо `./gradlew` используйте `gradlew.bat`. Готовый `.jar` кладётся в папку `mods`
клиента или сервера с Forge 1.20.1.

## Устройство репозитория

```
settings.gradle       список модов (include 'chess', 'knightcombat')
build.gradle          версия ForgeGradle для всех модов
gradle.properties     общие версии: Minecraft 1.20.1, Forge 47.4.28, маппинги
chess/                мод «Шахматы»: build.gradle, gradle.properties (id, имя, версия мода), src, tools, docs
knightcombat/         мод «Рыцарский бой»: так же
```

Новый мод — это ещё одна папка с `build.gradle`, `gradle.properties` и `src` (проще всего скопировать
папку одного из модов и поменять `mod_id`, пакет и имя) и строка `include '<папка>'` в `settings.gradle`.

## Полезные ссылки

- Документация Forge 1.20.1: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
- Загрузки Forge: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
