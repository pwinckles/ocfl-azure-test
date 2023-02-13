package com.example.ocfl;

import edu.wisc.library.ocfl.api.MutableOcflRepository;
import edu.wisc.library.ocfl.api.model.ObjectVersionId;
import edu.wisc.library.ocfl.core.OcflRepositoryBuilder;
import edu.wisc.library.ocfl.core.extension.storage.layout.config.HashedNTupleLayoutConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        log.info("Starting");

        var root = Paths.get(System.getenv("TEST_ROOT"));

        if (Files.notExists(root)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }

        var ocflRoot = root.resolve("ocfl-root");
        var ocflTemp = Files.createDirectories(root.resolve("ocfl-temp"));

        var repo = new OcflRepositoryBuilder()
                .defaultLayoutConfig(new HashedNTupleLayoutConfig())
                .storage(storage -> storage.fileSystem(ocflRoot))
                .workDir(ocflTemp)
                .buildMutable();

        var fileCount = getEnvVar("FILE_COUNT", 4);
        var fileSize = getEnvVar("FILE_SIZE", 64);
        var maxPerThread = getEnvVar("MAX_FILES_PER_THREAD", 30_000);
        var threadCount = getEnvVar("THREAD_COUNT", 10);

        var executor = Executors.newFixedThreadPool(threadCount);
        var stop = new AtomicBoolean(false);
        var startedLatch = new CountDownLatch(threadCount);
        var workers = new CopyOnWriteArrayList<Worker>();
        var startTime = Instant.now();
        final var stoppedSignal = new Object();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping");
            stop.set(true);
            synchronized (stoppedSignal) {
                try {
                    stoppedSignal.wait(TimeUnit.MINUTES.toMillis(10));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for threads to stop");
                }
            }
        }));

        log.info("Starting {} workers", threadCount);

        for (int i = 0; i < threadCount; i++) {
            var worker = new Worker("worker-" + i, repo, fileCount, fileSize, maxPerThread, stop, startedLatch);
            workers.add(worker);
            executor.execute(worker);
        }

        startedLatch.await();

        log.info(
                "Running. File count: {}; File size: {}; Max objects {}",
                fileCount,
                fileSize,
                threadCount * maxPerThread);

        executor.shutdown();

        var complete = executor.awaitTermination(5, TimeUnit.MINUTES);

        while (!complete) {
            var totalCount = workers.stream().mapToLong(Worker::count).sum();
            log.info("{} operations in {}", totalCount, Duration.between(startTime, Instant.now()));
            complete = executor.awaitTermination(5, TimeUnit.MINUTES);
        }

        var totalCount = workers.stream().mapToLong(Worker::count).sum();
        log.info("{} operations in {}", totalCount, Duration.between(startTime, Instant.now()));

        log.info("Exiting");
        synchronized (stoppedSignal) {
            stoppedSignal.notify();
        }
    }

    private static class Worker implements Runnable {
        private final String prefix;
        private final MutableOcflRepository repo;
        private final int fileCount;
        private final int fileSize;
        private final int maxFiles;
        private final AtomicBoolean stop;
        private final CountDownLatch startedLatch;
        private final AtomicInteger count = new AtomicInteger(0);
        private int nextNum = 0;

        private Worker(
                String prefix,
                MutableOcflRepository repo,
                int fileCount,
                int fileSize,
                int maxFiles,
                AtomicBoolean stop,
                CountDownLatch startedLatch) {
            this.prefix = prefix;
            this.repo = repo;
            this.fileCount = fileCount;
            this.fileSize = fileSize;
            this.maxFiles = maxFiles;
            this.stop = stop;
            this.startedLatch = startedLatch;
        }

        @Override
        public void run() {
            startedLatch.countDown();
            while (!stop.get()) {
                try {
                    var objectId = "urn:example:" + prefix + "-" + nextNum;
                    var files = IntStream.range(0, fileCount)
                            .mapToObj(i -> RandomStringUtils.randomAscii(fileSize))
                            .collect(Collectors.toList());
                    repo.purgeObject(objectId);
                    repo.stageChanges(ObjectVersionId.head(objectId), null, updater -> {
                        for (int i = 0; i < files.size(); i++) {
                            updater.writeFile(
                                    new ByteArrayInputStream(files.get(i).getBytes(StandardCharsets.UTF_8)),
                                    "file" + (i + 1) + ".txt");
                        }
                    });
                    repo.purgeObject(objectId);
                    repo.stageChanges(ObjectVersionId.head(objectId), null, updater -> {
                        for (int i = 0; i < files.size(); i++) {
                            updater.writeFile(
                                    new ByteArrayInputStream(files.get(i).getBytes(StandardCharsets.UTF_8)),
                                    "file" + (i + 1) + "-2.txt");
                        }
                    });
                    nextNum = count.incrementAndGet() % maxFiles;
                } catch (Exception e) {
                    log.error("Failed to create object", e);
                }
            }
            log.info("Worker {} exiting after {} operations", prefix, count());
        }

        public long count() {
            return count.get();
        }
    }

    private static int getEnvVar(String name, int defaultVal) {
        return Integer.parseInt(Objects.requireNonNullElse(System.getenv(name), String.valueOf(defaultVal)));
    }
}
