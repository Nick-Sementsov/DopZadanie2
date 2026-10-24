# Моды для Minecraft Forge 1.20.1

Проект для создания модов под **Minecraft 1.20.1** на **Forge 47.4.28**.
Основан на официальном MDK (Mod Development Kit) от Forge.

## Требования

- **JDK 17** (Minecraft 1.20.1 работает на Java 17). Если JDK 17 не установлен,
  Gradle скачает его сам через toolchain.
- IntelliJ IDEA или Eclipse (рекомендуется IntelliJ IDEA).
- Около 3 ГБ свободной оперативной памяти для первой сборки: Gradle декомпилирует Minecraft.

## Первый запуск

1. Откройте папку проекта в IntelliJ IDEA (`File → Open` → выбрать `build.gradle`)
   и дождитесь окончания импорта Gradle (первый раз это займёт несколько минут).
2. Сгенерируйте конфигурации запуска:
   ```bash
   ./gradlew genIntellijRuns     # для IntelliJ IDEA
   ./gradlew genEclipseRuns      # для Eclipse
   ```
   На Windows вместо `./gradlew` используйте `gradlew.bat`.
3. Обновите Gradle-проект в IDE, если нужно.

## Основные команды

| Команда                     | Что делает                                         |
|-----------------------------|----------------------------------------------------|
| `./gradlew runClient`       | Запускает клиент Minecraft с модом                 |
| `./gradlew runServer`       | Запускает выделенный сервер с модом                |
| `./gradlew runData`         | Запускает генераторы данных (`src/generated`)      |
| `./gradlew runGameTestServer` | Запускает игровые тесты (gametests)              |
| `./gradlew build`           | Собирает мод в `build/libs/<mod_id>-<версия>.jar`  |
| `./gradlew --refresh-dependencies` | Обновляет зависимости, если в IDE пропали библиотеки |
| `./gradlew clean`           | Очищает результаты сборки (код не затрагивается)   |

Готовый `.jar` из `build/libs/` кладётся в папку `mods` клиента или сервера с установленным Forge 1.20.1.

## Структура проекта

```
build.gradle                 # настройки сборки ForgeGradle
gradle.properties            # версии Minecraft/Forge и свойства мода (id, название, автор…)
settings.gradle
src/main/java/               # исходный код мода
  com/example/examplemod/
    ExampleMod.java          # главный класс мода (@Mod), пример блока, предмета и вкладки
    Config.java              # пример конфигурации мода
src/main/resources/
  META-INF/mods.toml         # описание мода для Forge (заполняется из gradle.properties)
  pack.mcmeta                # описание ресурс-пака мода
```

Текстуры, модели, локализация и прочие ресурсы кладутся в
`src/main/resources/assets/<mod_id>/`, а рецепты, теги и т.п. — в
`src/main/resources/data/<mod_id>/`.

## Как переименовать мод

Сейчас используется пример `examplemod`. Чтобы сделать свой мод:

1. В `gradle.properties` поменяйте `mod_id`, `mod_name`, `mod_authors`,
   `mod_description`, `mod_group_id` (и при желании `mod_license`).
   `mod_id` — только строчные латинские буквы, цифры и `_`, например `mymod`.
2. Переименуйте пакет `com.example.examplemod` под свой `mod_group_id`.
3. В `ExampleMod.java` поменяйте значение `MODID` так, чтобы оно совпадало с `mod_id`.
4. Выполните `./gradlew genIntellijRuns` (или `genEclipseRuns`) ещё раз.

## Полезные ссылки

- Документация Forge 1.20.1: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
- Загрузки Forge: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
- Лицензия на маппинги Mojang (official mappings): https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md
- Parchment (имена параметров и javadoc поверх official): https://parchmentmc.org/docs/getting-started
