package dev.enchantlibrary;

/** Per-version switches; src/mc<version>/java can hold a replacement copy of this class. */
final class VersionCompat {

    /** Left-click applies and right-click takes a book; true swaps them for versions that report the buttons the other way round. */
    static final boolean SWAP_MOUSE_BUTTONS = true;

    private VersionCompat() {
    }
}