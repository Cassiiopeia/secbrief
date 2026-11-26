package kr.suhsaechan.secbrief.web.admin.support;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;

/**
 * 표 쪽 번호 묶음: 지금 쪽(1부터), 전체 쪽 수, 화면에 보일 번호(지금 쪽 앞뒤 2개씩), 전체 건수
 * admin-ui 조각 pagination 이 그림
 */
public record AdminPagination(int currentPage, int totalPages, List<Integer> pageNumbers, long totalElements) {

    // 지금 쪽 양옆에 보일 번호 수
    private static final int NEIGHBOR_PAGE_COUNT = 2;

    public static AdminPagination of(Page<?> page) {
        int currentPage = page.getNumber() + 1;
        int totalPages = Math.max(page.getTotalPages(), 1);
        int firstPage = Math.max(1, currentPage - NEIGHBOR_PAGE_COUNT);
        int lastPage = Math.min(totalPages, currentPage + NEIGHBOR_PAGE_COUNT);
        return new AdminPagination(currentPage, totalPages,
                IntStream.rangeClosed(firstPage, lastPage).boxed().toList(), page.getTotalElements());
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    public boolean hasNextPage() {
        return currentPage < totalPages;
    }
}
