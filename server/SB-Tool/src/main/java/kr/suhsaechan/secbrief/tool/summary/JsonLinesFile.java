package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON Lines 파일 (한 줄에 JSON 객체 1개): 전체 읽기, 한 줄씩 이어 쓰기
 * 이어 쓰기는 줄마다 바로 비움 (중간에 멈춰도 끝난 건은 남아 이어서 돌릴 수 있음), 여러 스레드가 함께 써도 됨
 */
public final class JsonLinesFile {

    private JsonLinesFile() {
    }

    /** 파일이 없으면 빈 목록, 빈 줄은 건너뜀 */
    public static List<JsonNode> readAll(Path file) {
        List<JsonNode> rows = new ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        try {
            for (String line : Files.readAllLines(file, UTF_8)) {
                if (!line.isBlank()) {
                    rows.add(OBJECT_MAPPER.readTree(line));
                }
            }
            return rows;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static Appender openAppender(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            return new Appender(Files.newBufferedWriter(file, UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** 한 줄씩 이어 쓰는 쓰기 도구 (스레드 안전) */
    public static final class Appender implements AutoCloseable {

        private final BufferedWriter writer;

        private Appender(BufferedWriter writer) {
            this.writer = writer;
        }

        public synchronized void append(JsonNode row) {
            try {
                writer.write(OBJECT_MAPPER.writeValueAsString(row));
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public synchronized void close() {
            try {
                writer.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
