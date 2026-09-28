# Enchant Library (Fabric, Minecraft 26.2)

Java 25, Fabric Loader 0.19.3+, Fabric API for 26.2.

## Build
Easiest: generate a template at https://fabricmc.net/develop/template (Minecraft 26.2, mod id `enchantlibrary`)
to get the Gradle wrapper, then copy in `src/`, `build.gradle`, `gradle.properties`, `settings.gradle`.
Or with Gradle 9.5.1 installed: `gradle wrapper --gradle-version 9.5.1` then `./gradlew build`.
The jar lands in `build/libs/`.

## Craft
Obsidian / Bookshelf / Obsidian
Bookshelf / Enchanting Table / Bookshelf
Obsidian x3

## Use
- Right-click with an enchanted book: store it
- Right-click with an enchantable item: apply the selected enchantment at the library's level
- Sneak + right-click, empty hand: cycle the selected enchantment
- Right-click, empty hand: take out a book of the selected enchantment

## Leveling
A stored book of level L is worth 2^(L-1) points (two level-N books = level N+1).
Library level = highest L with 2^(L-1) <= points, capped at 2x the vanilla max
(Sharpness up to X; single-level enchants like Mending stay at I).
Taking a book out spends its points. Breaking the block drops everything as books.

Tweak in EnchantLibraryBlockEntity: LEVEL_CAP_MULTIPLIER, MAX_POINTS.

## Build the jar without installing anything (GitHub Actions)
1. Create a new GitHub repository and upload the contents of this folder (including the hidden `.github` folder).
2. Open the Actions tab -> "Build jar" -> wait for the green check.
3. Download the `enchant-library-jar` artifact, unzip it, and put `enchant-library-1.0.0.jar`
   (NOT the `-sources` jar) in your Minecraft `mods` folder together with Fabric API.
