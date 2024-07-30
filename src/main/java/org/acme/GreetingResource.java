package org.acme;

import com.zaxxer.hikari.HikariDataSource;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Path("/hello")
public class GreetingResource {

    @Inject
    HikariDataSource hikariDataSource;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        long startTime = System.nanoTime();

        int totalRecords = 100000;
        int batchSize = 10;
        int totalBatches = totalRecords / batchSize;
        int maxConcurrentThreads = 100;

        Semaphore semaphore = new Semaphore(maxConcurrentThreads);

        IntStream.range(0, totalBatches).forEach(batch -> {
            try {
                semaphore.acquire();
                executor.submit(() -> {
                    try {
                        insertUsersBatch(batch * batchSize, batchSize);
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        shutdownExecutor(executor);

        long duration = System.nanoTime() - startTime;
        System.out.println("Tempo total de execução: " + duration / 1_000_000_000.0 + " segundos");

        return "Hello from Quarkus REST";
    }

    private void insertUsersBatch(int startId, int batchSize) {
        System.out.println("Inserindo batch de " + batchSize + " registros começando no ID: " + startId + " na Thread: " + Thread.currentThread());
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        long batchStartTime = System.nanoTime();

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = startId; i < startId + batchSize; i++) {
                stmt.setString(1, "User" + i);
                stmt.setString(2, "user" + i + "@example.com");
                stmt.addBatch();
                if (i % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        long batchEndTime = System.nanoTime();
        System.out.println("Tempo de execução do batch: " + (batchEndTime - batchStartTime) / 1_000_000.0 + " ms");
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
