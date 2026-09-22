package com.chacha.ai;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 墨水屏专用高性能 Markdown 解析与排版渲染引擎
 * 针对电子纸高对比度、清晰度及代码/表格/列表阅读优化：
 * 1. 标题 (#, ##, ###) 加粗放大与层级分明排版；
 * 2. 代码块 (```) 独占全宽淡灰背景底色与纯黑左侧重音立柱边框，等宽字体；
 * 3. 行内代码 (`) 淡灰背景、等宽加粗；
 * 4. 粗体 (**)、斜体 (*)、粗斜体 (***)、删除线 (~~)；
 * 5. 引用块 (>) 左侧实心立柱与缩进；
 * 6. 无序列表 (- / * / +) 替换为圆点 • 并保持悬挂缩进；
 * 7. 有序列表 (1. 2.) 保持数字悬挂缩进；
 * 8. 表格 (| ... |) 自动等宽列宽对齐与网格化；
 * 9. 超链接 [文本](url) 下划线高亮。
 */
public class MarkdownParser {

    public static class TableData {
        public List<String> headers = new ArrayList<String>();
        public List<List<String>> rows = new ArrayList<List<String>>();
    }

    public static class ContentBlock {
        public static final int TYPE_TEXT = 0;
        public static final int TYPE_TABLE = 1;

        public int type;
        public CharSequence text;
        public TableData table;

        public ContentBlock(CharSequence text) {
            this.type = TYPE_TEXT;
            this.text = text;
        }

        public ContentBlock(TableData table) {
            this.type = TYPE_TABLE;
            this.table = table;
        }
    }

    // 匹配行内 Markdown 语法的统一正则（优先匹配代码块与长标记，杜绝歧义）
    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "(`[^`\\n]+`)" +                                                               // G1: 行内代码 `code`
            "|(\\*{3}(?!\\s)[^*\\n]+(?<!\\s)\\*{3})" +                                      // G2: ***粗斜体***
            "|(\\*{2}(?!\\s)[^*\\n]+(?<!\\s)\\*{2})" +                                      // G3: **粗体**
            "|(\\*(?!\\s)[^*\\n]+(?<!\\s)\\*)" +                                            // G4: *斜体*
            "|((?<=\\s|^|[\\p{Punct}])_{3}(?!\\s)[^_\\n]+(?<!\\s)_{3}(?=\\s|$|[\\p{Punct}]))" + // G5: ___粗斜体___
            "|((?<=\\s|^|[\\p{Punct}])_{2}(?!\\s)[^_\\n]+(?<!\\s)_{2}(?=\\s|$|[\\p{Punct}]))" + // G6: __粗体__
            "|((?<=\\s|^|[\\p{Punct}])_(?!\\s)[^_\\n]+(?<!\\s)_(?=\\s|$|[\\p{Punct}]))" +       // G7: _斜体_
            "|(~~(?!\\s)[^~\\n]+(?<!\\s)~~)" +                                              // G8: ~~删除线~~
            "|(\\[([^\\]\\n]+)\\]\\(([^)\\n]+)\\))"                                         // G9: [label](url), G10=label, G11=url
    );

    /**
     * 代码块与引用块墨水屏自定义背景跨行渲染器
     */
    public static class CodeBlockSpan implements LineBackgroundSpan, LeadingMarginSpan {
        private final int bgColor;
        private final int barColor;
        private final int margin;

        public CodeBlockSpan(int bgColor, int barColor, int margin) {
            this.bgColor = bgColor;
            this.barColor = barColor;
            this.margin = margin;
        }

        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline, int bottom,
                                   CharSequence text, int start, int end, int lnum) {
            int oldColor = p.getColor();
            // 墨水屏全宽灰阶背景
            p.setColor(bgColor);
            c.drawRect(left, top, right, bottom, p);
            // 左侧高对比度纯黑重音立柱 (3dp 宽)
            p.setColor(barColor);
            c.drawRect(left, top, left + 8, bottom, p);
            p.setColor(oldColor);
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return margin;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                      CharSequence text, int start, int end, boolean first, Layout layout) {
        }
    }

    public static class QuoteBlockSpan implements LineBackgroundSpan, LeadingMarginSpan {
        private final int barColor;
        private final int margin;

        public QuoteBlockSpan(int barColor, int margin) {
            this.barColor = barColor;
            this.margin = margin;
        }

        @Override
        public void drawBackground(Canvas c, Paint p, int left, int right, int top, int baseline, int bottom,
                                   CharSequence text, int start, int end, int lnum) {
            int oldColor = p.getColor();
            p.setColor(barColor);
            c.drawRect(left, top, left + 6, bottom, p);
            p.setColor(oldColor);
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return margin;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                      CharSequence text, int start, int end, boolean first, Layout layout) {
        }
    }

    public static SpannableStringBuilder parse(String markdown) {
        if (markdown == null || markdown.length() == 0) {
            return new SpannableStringBuilder("");
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lines = markdown.split("\r?\n", -1);
        int lineCount = lines.length;

        boolean inCodeBlock = false;
        String codeLang = null;
        StringBuilder codeBuffer = new StringBuilder();
        List<String> tableBuffer = new ArrayList<String>();

        for (int i = 0; i < lineCount; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 1. 代码块判断 ``` 或 ~~~
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (!tableBuffer.isEmpty()) {
                    appendTable(ssb, tableBuffer);
                    tableBuffer.clear();
                }

                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLang = trimmed.substring(3).trim();
                    codeBuffer.setLength(0);
                } else {
                    inCodeBlock = false;
                    appendCodeBlock(ssb, codeBuffer.toString(), codeLang);
                    codeLang = null;
                    codeBuffer.setLength(0);
                }
                continue;
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            // 2. 表格收集
            if (isTableLine(trimmed)) {
                tableBuffer.add(trimmed);
                continue;
            } else if (!tableBuffer.isEmpty()) {
                appendTable(ssb, tableBuffer);
                tableBuffer.clear();
            }

            // 3. 分割线 ---, ***, ___
            if (isHorizontalRule(trimmed)) {
                appendHorizontalRule(ssb);
                continue;
            }

            // 4. 标题 # ~ ######
            if (isHeading(trimmed)) {
                appendHeading(ssb, trimmed);
                continue;
            }

            // 5. 引用块 >
            if (isBlockquote(trimmed)) {
                appendBlockquote(ssb, trimmed);
                continue;
            }

            // 6. 无序列表 - , * , +
            if (isUnorderedList(line)) {
                appendUnorderedList(ssb, line);
                continue;
            }

            // 7. 有序列表 1. 2.
            if (isOrderedList(line)) {
                appendOrderedList(ssb, line);
                continue;
            }

            // 8. 普通段落行
            if (ssb.length() > 0 && !endsWithNewline(ssb)) {
                ssb.append("\n");
            }
            appendInline(ssb, line);
            if (i < lineCount - 1) {
                ssb.append("\n");
            }
        }

        // 处理未闭合的代码块或表格
        if (inCodeBlock && codeBuffer.length() > 0) {
            appendCodeBlock(ssb, codeBuffer.toString(), codeLang);
        }
        if (!tableBuffer.isEmpty()) {
            appendTable(ssb, tableBuffer);
        }

        return ssb;
    }

    /**
     * 单独解析行内样式（用于表格单元格等局部控件）
     */
    public static CharSequence parseInline(String text) {
        if (text == null || text.length() == 0) return "";
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        appendInline(ssb, text);
        return ssb;
    }

    /**
     * 解析 Markdown 表格数据结构
     */
    public static TableData parseTableData(List<String> tableLines) {
        if (tableLines == null || tableLines.size() < 2) return null;
        TableData data = new TableData();
        List<List<String>> allRows = new ArrayList<List<String>>();
        boolean hasSeparator = false;

        for (int i = 0; i < tableLines.size(); i++) {
            String line = tableLines.get(i);
            if (isTableSeparator(line)) {
                hasSeparator = true;
                continue;
            }
            String[] rawCells = line.split("\\|");
            List<String> rowCells = new ArrayList<String>();
            int startIdx = (rawCells.length > 0 && rawCells[0].trim().isEmpty()) ? 1 : 0;
            int endIdx = (rawCells.length > 0 && rawCells[rawCells.length - 1].trim().isEmpty()) ? rawCells.length - 1 : rawCells.length;
            for (int c = startIdx; c < endIdx; c++) {
                rowCells.add(rawCells[c].trim());
            }
            if (!rowCells.isEmpty()) {
                allRows.add(rowCells);
            }
        }

        if (allRows.isEmpty()) return null;
        if (allRows.get(0).size() < 2 && !hasSeparator) return null;

        data.headers = allRows.get(0);
        for (int r = 1; r < allRows.size(); r++) {
            data.rows.add(allRows.get(r));
        }
        return data;
    }

    /**
     * 将 Markdown 文本按表格与普通段落/代码块切分为复合 Block 列表，以支持原生 TableLayout 视图渲染
     */
    public static List<ContentBlock> parseBlocks(String markdown) {
        List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        if (markdown == null || markdown.length() == 0) {
            return blocks;
        }

        String[] lines = markdown.split("\r?\n", -1);
        int lineCount = lines.length;

        boolean inCodeBlock = false;
        StringBuilder textLinesBuffer = new StringBuilder();
        List<String> tableBuffer = new ArrayList<String>();

        for (int i = 0; i < lineCount; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 1. 代码块判断 ``` 或 ~~~
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (!tableBuffer.isEmpty()) {
                    TableData tableData = parseTableData(tableBuffer);
                    if (tableData != null && !tableData.headers.isEmpty()) {
                        flushTextBuffer(blocks, textLinesBuffer);
                        blocks.add(new ContentBlock(tableData));
                    } else {
                        for (String tl : tableBuffer) {
                            textLinesBuffer.append(tl).append("\n");
                        }
                    }
                    tableBuffer.clear();
                }

                inCodeBlock = !inCodeBlock;
                textLinesBuffer.append(line).append("\n");
                continue;
            }

            if (inCodeBlock) {
                textLinesBuffer.append(line).append("\n");
                continue;
            }

            // 2. 表格收集
            if (isTableLine(trimmed)) {
                tableBuffer.add(trimmed);
                continue;
            } else if (!tableBuffer.isEmpty()) {
                TableData tableData = parseTableData(tableBuffer);
                if (tableData != null && !tableData.headers.isEmpty()) {
                    flushTextBuffer(blocks, textLinesBuffer);
                    blocks.add(new ContentBlock(tableData));
                } else {
                    for (String tl : tableBuffer) {
                        textLinesBuffer.append(tl).append("\n");
                    }
                }
                tableBuffer.clear();
            }

            // 3. 常规行
            textLinesBuffer.append(line).append("\n");
        }

        // 处理尾部未闭合表格
        if (!tableBuffer.isEmpty()) {
            TableData tableData = parseTableData(tableBuffer);
            if (tableData != null && !tableData.headers.isEmpty()) {
                flushTextBuffer(blocks, textLinesBuffer);
                blocks.add(new ContentBlock(tableData));
            } else {
                for (String tl : tableBuffer) {
                    textLinesBuffer.append(tl).append("\n");
                }
            }
            tableBuffer.clear();
        }

        flushTextBuffer(blocks, textLinesBuffer);

        if (blocks.isEmpty()) {
            blocks.add(new ContentBlock(parse(markdown)));
        }

        return blocks;
    }

    private static void flushTextBuffer(List<ContentBlock> blocks, StringBuilder sb) {
        if (sb.length() > 0) {
            String txt = sb.toString();
            while (txt.endsWith("\n")) {
                txt = txt.substring(0, txt.length() - 1);
            }
            if (txt.length() > 0) {
                CharSequence parsed = parse(txt);
                blocks.add(new ContentBlock(parsed));
            }
            sb.setLength(0);
        }
    }

    private static void appendInline(SpannableStringBuilder out, String text) {
        if (text == null || text.length() == 0) return;

        Matcher matcher = INLINE_PATTERN.matcher(text);
        int lastIndex = 0;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // 先追加匹配项之前的纯文本
            if (start > lastIndex) {
                out.append(text.substring(lastIndex, start));
            }

            if (matcher.group(1) != null) {
                // `行内代码`
                String match = matcher.group(1);
                String code = match.substring(1, match.length() - 1);
                int s = out.length();
                out.append(" ").append(code).append(" ");
                out.setSpan(new TypefaceSpan("monospace"), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(0xFFFEF3C7), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(0.92f), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(0xFFB91C1C), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (matcher.group(2) != null || matcher.group(5) != null) {
                // ***粗斜体*** 或 ___粗斜体___
                String match = matcher.group(2) != null ? matcher.group(2) : matcher.group(5);
                String inner = match.substring(3, match.length() - 3);
                int s = out.length();
                appendInline(out, inner);
                out.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (matcher.group(3) != null || matcher.group(6) != null) {
                // **粗体** 或 __粗体__
                String match = matcher.group(3) != null ? matcher.group(3) : matcher.group(6);
                String inner = match.substring(2, match.length() - 2);
                int s = out.length();
                appendInline(out, inner);
                out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (matcher.group(4) != null || matcher.group(7) != null) {
                // *斜体* 或 _斜体_
                String match = matcher.group(4) != null ? matcher.group(4) : matcher.group(7);
                String inner = match.substring(1, match.length() - 1);
                int s = out.length();
                appendInline(out, inner);
                out.setSpan(new StyleSpan(Typeface.ITALIC), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (matcher.group(8) != null) {
                // ~~删除线~~
                String match = matcher.group(8);
                String inner = match.substring(2, match.length() - 2);
                int s = out.length();
                appendInline(out, inner);
                out.setSpan(new StrikethroughSpan(), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (matcher.group(9) != null) {
                // [label](url)
                String label = matcher.group(10);
                String url = matcher.group(11);
                int s = out.length();
                out.append(label);
                out.setSpan(new URLSpan(url), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new UnderlineSpan(), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(0xFF2563EB), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            lastIndex = end;
        }

        if (lastIndex < text.length()) {
            out.append(text.substring(lastIndex));
        }
    }

    private static void appendCodeBlock(SpannableStringBuilder ssb, String code, String lang) {
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }
        int blockStart = ssb.length();

        if (lang != null && lang.length() > 0) {
            int langStart = ssb.length();
            ssb.append("[代码: ").append(lang).append("]\n");
            ssb.setSpan(new StyleSpan(Typeface.BOLD), langStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new RelativeSizeSpan(0.85f), langStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(0xFF0284C7), langStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (code.endsWith("\n")) {
            code = code.substring(0, code.length() - 1);
        }

        int codeStart = ssb.length();
        ssb.append(code).append("\n");
        int blockEnd = ssb.length();

        ssb.setSpan(new TypefaceSpan("monospace"), codeStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.88f), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(0xFF0F172A), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new CodeBlockSpan(0xFFF1F5F9, 0xFF2563EB, 20), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendHeading(SpannableStringBuilder ssb, String trimmedLine) {
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }
        int level = 0;
        while (level < trimmedLine.length() && trimmedLine.charAt(level) == '#') {
            level++;
        }
        String content = trimmedLine.substring(level).trim();
        int start = ssb.length();
        appendInline(ssb, content);
        ssb.append("\n");
        int end = ssb.length();

        float size;
        int headingColor;
        switch (level) {
            case 1:
                size = 1.35f;
                headingColor = 0xFF0F172A;
                break;
            case 2:
                size = 1.22f;
                headingColor = 0xFF0D9488;
                break;
            case 3:
                size = 1.12f;
                headingColor = 0xFF1D4ED8;
                break;
            case 4:
                size = 1.06f;
                headingColor = 0xFF7C3AED;
                break;
            default:
                size = 1.02f;
                headingColor = 0xFF334155;
                break;
        }
        ssb.setSpan(new RelativeSizeSpan(size), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(headingColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendBlockquote(SpannableStringBuilder ssb, String trimmedLine) {
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }
        int start = ssb.length();
        String content = trimmedLine.replaceFirst("^>+\\s*", "");
        appendInline(ssb, content);
        ssb.append("\n");
        int end = ssb.length();

        ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(0xFF334155), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new QuoteBlockSpan(0xFFD97706, 20), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendUnorderedList(SpannableStringBuilder ssb, String line) {
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        String trimmed = line.substring(indent);
        String content = trimmed.substring(2);

        int start = ssb.length();
        ssb.append("• ");
        appendInline(ssb, content);
        ssb.append("\n");
        int end = ssb.length();

        int margin = 16 + indent * 8;
        ssb.setSpan(new LeadingMarginSpan.Standard(margin, margin + 14), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendOrderedList(SpannableStringBuilder ssb, String line) {
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        String trimmed = line.substring(indent);
        int dotIdx = trimmed.indexOf('.');
        String num = trimmed.substring(0, dotIdx + 1);
        String content = trimmed.substring(dotIdx + 1).trim();

        int start = ssb.length();
        ssb.append(num).append(" ");
        appendInline(ssb, content);
        ssb.append("\n");
        int end = ssb.length();

        int margin = 16 + indent * 8;
        ssb.setSpan(new LeadingMarginSpan.Standard(margin, margin + 16), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendHorizontalRule(SpannableStringBuilder ssb) {
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }
        int start = ssb.length();
        ssb.append("────────────────────────\n");
        int end = ssb.length();
        ssb.setSpan(new ForegroundColorSpan(0xFF888888), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.8f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendTable(SpannableStringBuilder ssb, List<String> tableLines) {
        if (tableLines == null || tableLines.isEmpty()) return;
        if (ssb.length() > 0 && !endsWithNewline(ssb)) {
            ssb.append("\n");
        }

        List<List<String>> rows = new ArrayList<List<String>>();
        int maxCols = 0;
        int separatorRowIndex = -1;

        for (int r = 0; r < tableLines.size(); r++) {
            String line = tableLines.get(r);
            if (isTableSeparator(line)) {
                separatorRowIndex = r;
                continue;
            }
            String[] rawCells = line.split("\\|");
            List<String> rowCells = new ArrayList<String>();
            int startIdx = (rawCells.length > 0 && rawCells[0].trim().isEmpty()) ? 1 : 0;
            int endIdx = (rawCells.length > 0 && rawCells[rawCells.length - 1].trim().isEmpty()) ? rawCells.length - 1 : rawCells.length;
            for (int c = startIdx; c < endIdx; c++) {
                rowCells.add(rawCells[c].trim());
            }
            if (rowCells.size() > maxCols) {
                maxCols = rowCells.size();
            }
            rows.add(rowCells);
        }

        if (rows.isEmpty() || maxCols == 0) return;

        // 计算每列最大视觉宽度（中文按 2 字符计算）
        int[] colWidths = new int[maxCols];
        for (List<String> row : rows) {
            for (int c = 0; c < row.size() && c < maxCols; c++) {
                int w = getDisplayWidth(row.get(c));
                if (w > colWidths[c]) {
                    colWidths[c] = w;
                }
            }
        }

        StringBuilder formatted = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            formatted.append("|");
            for (int c = 0; c < maxCols; c++) {
                String cell = (c < row.size()) ? row.get(c) : "";
                int w = getDisplayWidth(cell);
                formatted.append(" ").append(cell);
                int pad = colWidths[c] - w;
                for (int p = 0; p < pad; p++) {
                    formatted.append(" ");
                }
                formatted.append(" |");
            }
            formatted.append("\n");

            if (r == 0 && (separatorRowIndex >= 0 || rows.size() > 1)) {
                formatted.append("|");
                for (int c = 0; c < maxCols; c++) {
                    formatted.append("-");
                    for (int p = 0; p < colWidths[c]; p++) {
                        formatted.append("-");
                    }
                    formatted.append("-|");
                }
                formatted.append("\n");
            }
        }

        int blockStart = ssb.length();
        ssb.append(formatted.toString());
        int blockEnd = ssb.length();

        ssb.setSpan(new TypefaceSpan("monospace"), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(0.85f), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(0xFF000000), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new CodeBlockSpan(0xFFF7F7F7, 0xFF444444, 16), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int getDisplayWidth(String s) {
        if (s == null) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += (c > 127) ? 2 : 1;
        }
        return w;
    }

    private static boolean isTableLine(String trimmed) {
        if (!trimmed.contains("|")) return false;
        if (trimmed.startsWith("|")) return true;
        int count = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '|') count++;
        }
        return count >= 2;
    }

    private static boolean isTableSeparator(String line) {
        String s = line.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").trim();
        return s.length() == 0;
    }

    private static boolean isHeading(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (!t.startsWith("#")) return false;
        int count = 0;
        while (count < t.length() && t.charAt(count) == '#') {
            count++;
        }
        return count >= 1 && count <= 6 && count < t.length() && t.charAt(count) == ' ';
    }

    private static boolean isBlockquote(String line) {
        if (line == null) return false;
        return line.trim().startsWith(">");
    }

    private static boolean isHorizontalRule(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (t.length() < 3) return false;
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) != c && t.charAt(i) != ' ') return false;
        }
        return true;
    }

    private static boolean isUnorderedList(String line) {
        if (line == null) return false;
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        if (i + 1 >= line.length()) return false;
        char c = line.charAt(i);
        return (c == '-' || c == '*' || c == '+') && line.charAt(i + 1) == ' ';
    }

    private static boolean isOrderedList(String line) {
        if (line == null) return false;
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        int startDigits = i;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        return i > startDigits && i + 1 < line.length() && line.charAt(i) == '.' && line.charAt(i + 1) == ' ';
    }

    private static boolean endsWithNewline(SpannableStringBuilder ssb) {
        if (ssb.length() == 0) return true;
        char c = ssb.charAt(ssb.length() - 1);
        return c == '\n';
    }
}
