package kr.suhsaechan.secbrief.tool.summary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 건별 작업을 정해 둔 개수만큼 동시에 돌림 (claude -p 호출이 건마다 수 초 걸려 순서대로 하면 너무 느림)
 * 한 건이 예외를 던지면 아직 시작하지 않은 건은 건너뛰고 그 예외를 다시 던짐 (제공자 장애 때 남은 호출을 쓰지 않게)
 */
public final class ParallelTaskRunner {

    private ParallelTaskRunner() {
    }

    public static <T> void forEach(List<T> items, int concurrency, Consumer<T> task) {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (T item : items) {
                futures.add(executor.submit(() -> task.accept(item)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", exception);
        } catch (ExecutionException exception) {
            throw exception.getCause() instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException(exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }
}
