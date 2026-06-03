package dev.limucc.trashventory.trash;

import dev.limucc.trashventory.Trashventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Saves/loads a player's bin to {@code <world>/trashventory/<uuid>.nbt} so a disconnect or server
 * restart never deletes items: the countdown simply freezes until the player returns. Item stacks are
 * encoded with {@link ItemStack#CODEC} (full data components preserved).
 */
public final class TrashPersistence {

    private TrashPersistence() {}

    private static Path fileFor(Path dir, UUID id) {
        return dir.resolve(id.toString() + ".nbt");
    }

    public static void save(Path dir, UUID id, TrashSession session, HolderLookup.Provider registries) {
        try {
            Files.createDirectories(dir);
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);

            CompoundTag items = new CompoundTag();
            for (int i = 0; i < session.container.getContainerSize(); i++) {
                ItemStack stack = session.container.getItem(i);
                if (stack.isEmpty()) continue;
                final int slot = i;
                ItemStack.CODEC.encodeStart(ops, stack).result()
                        .ifPresent(tag -> items.put(Integer.toString(slot), tag));
            }

            CompoundTag root = new CompoundTag();
            root.putInt("Size", session.size);
            root.putInt("Remaining", session.remainingTicks);
            root.putBoolean("Counting", session.countingDown);
            root.put("Items", items);

            NbtIo.writeCompressed(root, fileFor(dir, id));
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to save trash for {}", id, e);
        }
    }

    /** @return a restored session, or null if there is no file / it could not be read. */
    public static TrashSession load(Path dir, UUID id, HolderLookup.Provider registries) {
        Path file = fileFor(dir, id);
        if (!Files.exists(file)) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (root == null) return null;
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);

            int size = root.getIntOr("Size", 54);
            TrashSession session = new TrashSession(size);
            session.remainingTicks = root.getIntOr("Remaining", 0);
            session.countingDown = root.getBooleanOr("Counting", false);

            CompoundTag items = root.getCompoundOrEmpty("Items");
            for (int i = 0; i < size; i++) {
                String key = Integer.toString(i);
                if (!items.contains(key)) continue;
                Tag tag = items.get(key);
                if (tag == null) continue;
                final int slot = i;
                ItemStack.CODEC.parse(ops, tag).result()
                        .ifPresent(stack -> session.container.setItem(slot, stack));
            }
            return session;
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to load trash for {}", id, e);
            return null;
        }
    }

    public static void delete(Path dir, UUID id) {
        try {
            Files.deleteIfExists(fileFor(dir, id));
        } catch (IOException e) {
            Trashventory.LOGGER.error("Failed to delete trash for {}", id, e);
        }
    }
}
