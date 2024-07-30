package org.acme;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SemaphoreExample {
    private static final int MAX_CONCURRENT_THREADS = 3;
    private static final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_THREADS);

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            int taskNumber = i;
            executor.submit(() -> {
                try {
                    System.out.println("Thread " + taskNumber + " is waiting for a permit.");
                    semaphore.acquire();
                    System.out.println("Thread " + taskNumber + " acquired a permit.");

                    // Simulate some work with the shared resource
                    Thread.sleep(2000);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    System.out.println("Thread " + taskNumber + " released a permit.");
                    semaphore.release();
                }
            });
        }

        executor.shutdown();
    }
}
