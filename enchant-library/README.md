# Enchant Library (Fabric, Minecraft 26.1 / 26.2 / 26.3)

Java 25, Fabric Loader 0.19.3+, Fabric API for 26.2.

## Build
Easiest: generate a template at https://fabricmc.net/develop/template (Minecraft 26.2, mod id `enchantlibrary`)
to get the Gradle wrapper, then copy in `src/`, `build.gradle`, `gradle.properties`, `settings.gradle`.
Or with Gradle 9.5.1 installed: `gradle wrapper --gradle-version 9.5.1` then `./gradlew build`.
The jar lands in `build/libs/`.
Pick the Minecraft version with `-Pmc=26.1`, `-Pmc=26.2` (default) or `-Pmc=26.3`; versions live in `versions/`.
GitHub Actions builds all three; each run has one jar artifact per version.

## Craft
Obsidian / Bookshelf / Obsidian
Bookshelf / Enchanting Table / Bookshelf
Obsidian x3

## Use
Right-click the block to open the library screen - a custom drawn interface, not a reskinned chest.

**Left: your collection.** A scrolling list (mouse wheel) of every stored enchantment, each
showing its name, current level, and a progress bar toward the next level. Hovering a row
shows its details underneath: level of max (and the vanilla max), points stored, and what
applying the chosen level would cost.
- Left-click a row: apply that enchantment to the item in the anvil slot
- Right-click a row: take out a book of that enchantment

**Right: the two slots.**
- Books slot (top): drop enchanted books here to add them; shift-clicking books in your
  inventory sends them here too
- Item slot (bottom): the item to enchant
- Under the item slot, the `- max +` control picks which level to apply (max = the highest the
  library has)

Status messages appear in chat, so they stay readable while the screen is open.

## Balance
- Tiers: a level-L book is worth 3^(L-1) points, so 3 books of one level make the next level
  (81 level-I books for level V). The library level is capped at 2x the vanilla max (Sharpness X, Mending I).
- XP: putting level L on an item costs 10 x L x L experience points (I = 10, II = 40, III = 90, IV = 160, V = 250).
  Upgrading an enchantment on an item only costs the difference. Creative players pay nothing.
- Enchanting an item also spends the points of that enchantment level from the library
  (same as the book of that level; upgrades only spend the difference).
- Taking a book out spends its points. Breaking the block drops everything as books.

Tweak in EnchantLibraryBlockEntity: TIER_MULTIPLIER, LEVEL_CAP_MULTIPLIER, XP_COST_FACTOR, STORAGE_TOP_BOOKS.
