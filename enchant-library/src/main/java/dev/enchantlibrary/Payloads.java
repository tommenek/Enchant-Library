package dev.enchantlibrary;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The packets the library screen uses to talk to the server. */
public final class Payloads {

    private Payloads() {
    }

    /** One row in the collection, already worked out server-side so the client just draws it. */
    public record Entry(String id, String name, int level, int cap, int vanillaMax, int points,
                        int nextLevelPoints) {
    }

    /** Server -> client: the whole collection, sent on open and after every change. */
    public record LibraryData(List<Entry> entries) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<LibraryData> TYPE =
                new CustomPacketPayload.Type<>(EnchantLibraryMod.id("library_data"));

        public static final StreamCodec<FriendlyByteBuf, LibraryData> CODEC =
                StreamCodec.of(LibraryData::write, LibraryData::read);

        private static void write(FriendlyByteBuf buf, LibraryData payload) {
            buf.writeVarInt(payload.entries.size());
            for (Entry entry : payload.entries) {
                buf.writeUtf(entry.id());
                buf.writeUtf(entry.name());
                buf.writeVarInt(entry.level());
                buf.writeVarInt(entry.cap());
                buf.writeVarInt(entry.vanillaMax());
                buf.writeVarInt(entry.points());
                buf.writeVarInt(entry.nextLevelPoints());
            }
        }

        private static LibraryData read(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new LibraryData(entries);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: do something with one enchantment. */
    public record LibraryAction(String id, int action, int level) implements CustomPacketPayload {

        /** Put the enchantment on the item in the anvil slot, at {@code level} (0 = highest). */
        public static final int APPLY = 0;
        /** Take a book of this enchantment out of the library. */
        public static final int EXTRACT = 1;

        public static final CustomPacketPayload.Type<LibraryAction> TYPE =
                new CustomPacketPayload.Type<>(EnchantLibraryMod.id("library_action"));

        public static final StreamCodec<FriendlyByteBuf, LibraryAction> CODEC =
                StreamCodec.of(LibraryAction::write, LibraryAction::read);

        private static void write(FriendlyByteBuf buf, LibraryAction payload) {
            buf.writeUtf(payload.id);
            buf.writeVarInt(payload.action);
            buf.writeVarInt(payload.level);
        }

        private static LibraryAction read(FriendlyByteBuf buf) {
            return new LibraryAction(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
