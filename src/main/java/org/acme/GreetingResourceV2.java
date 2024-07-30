package org.acme;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Path("/hello")
public class GreetingResourceV2 {

    @Inject
    HikariDataSource hikariDataSource;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(List<User> users) {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        long startTime = System.nanoTime();

        int batchSize = 10; // Ajustar o tamanho do batch
        int totalBatches = (int) Math.ceil((double) users.size() / batchSize);
        int maxConcurrentThreads = 100; // Ajustar conforme necessário

        //Iniciação do Semaphore com o número máximo de threads que podem acessar o recurso ao mesmo tempo.
        Semaphore semaphore = new Semaphore(maxConcurrentThreads); // Limitar a 50 threads concorrentes

        for (int batch = 0; batch < totalBatches; batch++) {
            final int start = batch * batchSize;
            final int end = Math.min(start + batchSize, users.size());

            try {
                //Cada thread tenta adquirir uma permissão usando semaphore.acquire(). Se uma permissão estiver disponível,
                // a thread prossegue; caso contrário, ela é bloqueada até que uma permissão seja liberada.
                semaphore.acquire(); // Adquirir permissão
                // Submeter uma tarefa ao executor
                executor.submit(() -> {
                    try {
                        insertUsersBatch(users.subList(start, end));
                    } finally {
                        //Após completar seu trabalho, a thread libera a permissão com semaphore.release(),
                        // permitindo que outras threads possam adquirir essa permissão e acessar o recurso.
                        semaphore.release(); // Liberar permissão
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Encerrar o executor de forma ordenada
        shutdownExecutor(executor);

        long duration = System.nanoTime() - startTime;
        System.out.println("Tempo total de execução: " + duration / 1_000_000_000.0 + " segundos");

        return "Users inserted successfully";
    }

    private void insertUsersBatch(List<User> usersBatch) {
        System.out.println("Inserindo batch de " + usersBatch.size() + " registros na Thread: " + Thread.currentThread());
        String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
        long batchStartTime = System.nanoTime();

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (User user : usersBatch) {
                stmt.setString(1, user.getName());
                stmt.setString(2, user.getEmail());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        long batchEndTime = System.nanoTime();
        System.out.println("Tempo de execução do batch: " + (batchEndTime - batchStartTime) / 1_000_000.0 + " ms");
    }

    private void shutdownExecutor(ExecutorService executor) {
        // Solicitar o encerramento do executor
        executor.shutdown();
        try {
            // Aguardar a conclusão das tarefas por até 60 minutos
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                // Forçar o encerramento do executor
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // Forçar o encerramento do executor em caso de interrupção
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}