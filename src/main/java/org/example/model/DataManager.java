package org.example.model;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataManager {
    private static DataManager instance;
    private static final Path FILE_PATH = Path.of("data.dat");
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();

    private DataManager() {
    }

    public static DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    public StoreSnapshot saveStore(AuctionStore store) {
        AuctionStore safeStore = store == null ? AuctionStore.empty() : store;

        fileLock.writeLock().lock();
        try {
            return writeStoreToDisk(safeStore, null)
                    .orElseGet(this::readSnapshotFromDisk);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    public Optional<StoreSnapshot> saveStoreIfVersionMatches(AuctionStore store, long expectedVersion) {
        AuctionStore safeStore = store == null ? AuctionStore.empty() : store;

        fileLock.writeLock().lock();
        try {
            return writeStoreToDisk(safeStore, Math.max(0L, expectedVersion));
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    public void saveItems(List<Item> items) {
        saveStore(new AuctionStore(items, Map.of()));
    }

    public StoreSnapshot loadSnapshot() {
        fileLock.readLock().lock();
        try {
            return readSnapshotFromDisk();
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public AuctionStore loadStore() {
        return loadSnapshot().store();
    }

    public List<Item> loadItems() {
        AuctionStore store = loadStore();
        return store.getItems().isEmpty() ? Collections.emptyList() : store.getItems();
    }

    private StoreSnapshot readSnapshotFromDisk() {
        if (!Files.exists(FILE_PATH)) {
            System.out.println("No data file yet. Starting with an empty catalog.");
            return new StoreSnapshot(AuctionStore.empty(), 0L);
        }

        try (RandomAccessFile file = new RandomAccessFile(FILE_PATH.toFile(), "r");
             FileChannel channel = file.getChannel();
             FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
            if (channel.size() == 0L) {
                return new StoreSnapshot(AuctionStore.empty(), 0L);
            }

            long version = resolveVersion(channel);
            channel.position(0L);
            ObjectInputStream ois = new ObjectInputStream(Channels.newInputStream(channel));
            Object loadedObject = ois.readObject();
            return new StoreSnapshot(deserializeStore(loadedObject), version);
        } catch (FileNotFoundException e) {
            System.out.println("No data file yet. Starting with an empty catalog.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to read file: " + e.getMessage());
        }
        return new StoreSnapshot(AuctionStore.empty(), 0L);
    }

    private AuctionStore deserializeStore(Object loadedObject) {
        if (loadedObject instanceof AuctionStore store) {
            return store;
        }

        // Keep old List<Item> files readable so existing data keeps working.
        if (loadedObject instanceof List<?> rawList) {
            List<Item> migratedItems = new ArrayList<>();
            for (Object candidate : rawList) {
                if (candidate instanceof Item item) {
                    migratedItems.add(item);
                }
            }
            return new AuctionStore(migratedItems, Map.of());
        }

        return AuctionStore.empty();
    }

    private Optional<StoreSnapshot> writeStoreToDisk(AuctionStore safeStore, Long expectedVersion) {
        try (RandomAccessFile file = new RandomAccessFile(FILE_PATH.toFile(), "rw");
             FileChannel channel = file.getChannel();
             FileLock ignored = channel.lock()) {
            long currentVersion = resolveVersion(channel);
            if (expectedVersion != null && currentVersion != expectedVersion) {
                return Optional.empty();
            }

            channel.truncate(0L);
            channel.position(0L);
            ObjectOutputStream oos = new ObjectOutputStream(Channels.newOutputStream(channel));
            oos.writeObject(safeStore);
            oos.flush();
            channel.force(true);
            long version = resolveVersion(channel);
            System.out.println("Saved data to " + FILE_PATH);
            return Optional.of(new StoreSnapshot(safeStore, version));
        } catch (IOException e) {
            System.err.println("Failed to save file: " + e.getMessage());
            return Optional.empty();
        }
    }

    private long resolveVersion() {
        if (!Files.exists(FILE_PATH)) {
            return 0L;
        }

        try {
            long modifiedTime = Files.getLastModifiedTime(FILE_PATH).to(TimeUnit.NANOSECONDS);
            long size = Files.size(FILE_PATH);
            return Math.max(0L, modifiedTime * 31L + size);
        } catch (IOException e) {
            System.err.println("Failed to inspect file version: " + e.getMessage());
            return 0L;
        }
    }

    private long resolveVersion(FileChannel channel) {
        try {
            long modifiedTime = Files.exists(FILE_PATH)
                    ? Files.getLastModifiedTime(FILE_PATH).to(TimeUnit.NANOSECONDS)
                    : 0L;
            long size = channel.size();
            return Math.max(0L, modifiedTime * 31L + size);
        } catch (IOException e) {
            System.err.println("Failed to inspect file version: " + e.getMessage());
            return 0L;
        }
    }
}
