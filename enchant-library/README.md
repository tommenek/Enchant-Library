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
Right-click the block to open the library screen.
- Click with an enchanted book on the list (or shift-click a book in your inventory): store it
- Put an item in the middle bottom slot, pick the level with the experience-bottle button
  (left-click +1, right-click -1, shift-click = highest available), then left-click an enchantment
- Right-click or shift-click an enchantment: take out a book of that enchantment

## Balance
- Tiers: a level-L book is worth 3^(L-1) points, so 3 books of one level make the next level
  (81 level-I books for level V). The library level is capped at 2x the vanilla max (Sharpness X, Mending I).
- XP: putting level L on an item costs 10 x L x L experience points (I = 10, II = 40, III = 90, IV = 160, V = 250).
  Upgrading an enchantment on an item only costs the difference. Creative players pay nothing.
- Enchanting an item also spends the points of that enchantment level from the library
  (same as the book of that level; upgrades only spend the difference).
- Taking a book out spends its points. Breaking the block drops everything as books.

Tweak in EnchantLibraryBlockEntity: TIER_MULTIPLIER, LEVEL_CAP_MULTIPLIER, XP_COST_FACTOR, STORAGE_TOP_BOOKS.
