package kr.suhsaechan.secbrief.advisory.refetch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * HTML 표 > 행마다 한 묶음 글: [머리글: 칸 | 머리글: 칸] (세로 병합 칸은 행마다 반복, 머리글 없으면 칸만)
 * 공백을 접는 정리(SourceTextPolicy) 뒤에도 제품 <-> 영향·해결 버전 짝이 한 행 안에 남게 함 (KISA 공지 표, #57)
 */
final class SourcePageTableText {

    static final String ROW_START = "[";
    static final String ROW_END = "]";
    static final String CELL_SEPARATOR = " | ";
    static final String LABEL_SEPARATOR = ": ";
    private static final String ROW_SEPARATOR = " ";
    private static final String TABLE_ROWS = "> thead > tr, > tbody > tr, > tfoot > tr, > tr";
    private static final String HEADER_CELL = "th";
    private static final String TABLE_CELLS = "th, td";

    private SourcePageTableText() {
    }

    /**
     * 칸 안의 가장 바깥 표 > 행 글 (넘긴 요소에 직접 적용, 호출부는 복사본을 넘김)
     */
    static void replaceTablesWithRowText(Element container) {
        List<Element> outermostTables = container.select("table").stream()
                .filter(table -> table.parents().stream().noneMatch(parent -> "table".equals(parent.tagName())))
                .toList();
        for (Element table : outermostTables) {
            table.replaceWith(new TextNode(ROW_SEPARATOR + toRowText(table) + ROW_SEPARATOR));
        }
    }

    /**
     * 표 1개 > 행 글: 칸 펼치기(rowspan·colspan) > 머리글 행(모든 칸이 th) 기억 > 본문 행마다 [머리글: 값 | ...]
     */
    static String toRowText(Element table) {
        List<String> headerLabels = List.of();
        List<String> rowTexts = new ArrayList<>();
        for (ExpandedRow row : expandRows(table)) {
            if (row.isHeader()) {
                headerLabels = row.cellTexts();
                continue;
            }
            String rowText = toLabeledCells(headerLabels, row.cellTexts());
            if (!rowText.isEmpty()) {
                rowTexts.add(ROW_START + rowText + ROW_END);
            }
        }
        return String.join(ROW_SEPARATOR, rowTexts);
    }

    // 빈 칸은 뺌, 머리글이 없거나 값과 같으면 값만
    private static String toLabeledCells(List<String> headerLabels, List<String> cellTexts) {
        List<String> labeledCells = new ArrayList<>();
        for (int column = 0; column < cellTexts.size(); column++) {
            String cellText = cellTexts.get(column);
            if (cellText.isBlank()) {
                continue;
            }
            String label = column < headerLabels.size() ? headerLabels.get(column) : "";
            labeledCells.add(label.isBlank() || label.equals(cellText) ? cellText : label + LABEL_SEPARATOR + cellText);
        }
        return String.join(CELL_SEPARATOR, labeledCells);
    }

    // 행마다 열 위치에 칸 글 채우기: 위 행의 rowspan 이 남은 열은 그 글을 다시 씀
    private static List<ExpandedRow> expandRows(Element table) {
        List<ExpandedRow> expandedRows = new ArrayList<>();
        Map<Integer, SpanningCell> spanningCellByColumn = new HashMap<>();
        for (Element row : table.select(TABLE_ROWS)) {
            List<Element> cells = row.children().stream().filter(cell -> cell.is(TABLE_CELLS)).toList();
            if (cells.isEmpty()) {
                continue;
            }
            List<String> cellTexts = new ArrayList<>();
            int cellIndex = 0;
            int column = 0;
            while (cellIndex < cells.size() || spanningCellByColumn.containsKey(column)) {
                SpanningCell spanningCell = spanningCellByColumn.get(column);
                if (spanningCell != null) {
                    cellTexts.add(spanningCell.text());
                    spanningCellByColumn.compute(column, (key, cell) -> cell.remainingRows() <= 1 ? null : cell.nextRow());
                    column++;
                    continue;
                }
                Element cell = cells.get(cellIndex++);
                String cellText = cell.text().strip();
                int columnSpan = toSpan(cell.attr("colspan"));
                int rowSpan = toSpan(cell.attr("rowspan"));
                // 가로 병합은 첫 열에만 글 (뒤 열 머리글에 같은 값을 붙이면 없는 짝이 생김), 세로 병합은 행마다 반복
                for (int spannedColumn = 0; spannedColumn < columnSpan; spannedColumn++) {
                    String spannedText = spannedColumn == 0 ? cellText : "";
                    cellTexts.add(spannedText);
                    if (rowSpan > 1) {
                        spanningCellByColumn.put(column, new SpanningCell(spannedText, rowSpan - 1));
                    }
                    column++;
                }
            }
            boolean isHeader = cells.stream().allMatch(cell -> cell.is(HEADER_CELL));
            expandedRows.add(new ExpandedRow(cellTexts, isHeader));
        }
        return expandedRows;
    }

    // 병합 칸 수 (없음·숫자 아님·1 미만 > 1)
    private static int toSpan(String spanAttribute) {
        try {
            return Math.max(1, Integer.parseInt(spanAttribute.strip()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    /** 펼친 행 1개: 열 순서의 칸 글, 머리글 행인지 */
    private record ExpandedRow(List<String> cellTexts, boolean isHeader) {
    }

    /** 아래 행으로 이어지는 병합 칸: 글, 남은 행 수 */
    private record SpanningCell(String text, int remainingRows) {

        SpanningCell nextRow() {
            return new SpanningCell(text, remainingRows - 1);
        }
    }
}
