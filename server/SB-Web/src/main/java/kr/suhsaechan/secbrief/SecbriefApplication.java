package kr.suhsaechan.secbrief;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 서버 시작점: 전 모듈(kr.suhsaechan.secbrief 하위) 스캔
 */
@SpringBootApplication
public class SecbriefApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecbriefApplication.class, args);
    }
}
