package com.chacha.ai;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.List;

/**
 * 墨水屏专用 Markdown 原生表格渲染器
 * 将 Markdown 表格数据结构渲染为带横向平滑滚动、圆角卡片边框、浅蓝表头与斑马纹数据行的原生 TableLayout 视图。
 */
public class MarkdownTableRenderer {

    public static View createTableView(Context context, MarkdownParser.TableData tableData, int fontSize) {
        if (context == null || tableData == null) {
            return new View(context);
        }

        float density = context.getResources().getDisplayMetrics().density;
        int dp1 = Math.max(1, (int) (1 * density));
        int dp6 = (int) (6 * density);
        int dp8 = (int) (8 * density);
        int dp12 = (int) (12 * density);

        // 外层水平滑动容器：即使表格列数很多、内容超长，也保证平滑横向滚动，绝不挤压错行
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.topMargin = dp6;
        hsvLp.bottomMargin = dp6;
        hsv.setLayoutParams(hsvLp);
        hsv.setFillViewport(true);
        hsv.setHorizontalScrollBarEnabled(true);
        hsv.setScrollbarFadingEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // 内层卡片容器
        LinearLayout cardContainer = new LinearLayout(context);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setBackgroundResource(R.drawable.table_card);
        cardContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TableLayout tableLayout = new TableLayout(context);
        tableLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tableLayout.setStretchAllColumns(true);

        int colCount = tableData.headers.size();
        for (List<String> row : tableData.rows) {
            if (row.size() > colCount) {
                colCount = row.size();
            }
        }

        if (colCount == 0) {
            return new View(context);
        }

        // 1. 渲染表头行（若存在）
        if (!tableData.headers.isEmpty()) {
            TableRow headerRow = new TableRow(context);
            headerRow.setBackgroundColor(Color.parseColor("#E0F2FE")); // 清爽浅天蓝底色
            headerRow.setLayoutParams(new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int c = 0; c < colCount; c++) {
                String headerText = (c < tableData.headers.size()) ? tableData.headers.get(c) : "";
                TextView tvHeader = new TextView(context);
                tvHeader.setText(MarkdownParser.parseInline(headerText));
                tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
                tvHeader.setTextColor(Color.parseColor("#0F172A"));
                tvHeader.setTextSize(Math.max(14, fontSize - 2));
                tvHeader.setPadding(dp12, dp8, dp12, dp8);
                tvHeader.setGravity(Gravity.CENTER_VERTICAL);
                tvHeader.setMinEms(3);
                headerRow.addView(tvHeader);

                // 单元格垂直分割线
                if (c < colCount - 1) {
                    View vDivider = new View(context);
                    TableRow.LayoutParams vLp = new TableRow.LayoutParams(dp1, ViewGroup.LayoutParams.MATCH_PARENT);
                    vDivider.setLayoutParams(vLp);
                    vDivider.setBackgroundColor(Color.parseColor("#BAE6FD"));
                    headerRow.addView(vDivider);
                }
            }
            tableLayout.addView(headerRow);

            // 表头底部分割线
            View headerBottomDivider = new View(context);
            headerBottomDivider.setLayoutParams(new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp1));
            headerBottomDivider.setBackgroundColor(Color.parseColor("#CBD5E1"));
            tableLayout.addView(headerBottomDivider);
        }

        // 2. 渲染数据行
        for (int r = 0; r < tableData.rows.size(); r++) {
            List<String> rowData = tableData.rows.get(r);
            TableRow dataRow = new TableRow(context);
            // 斑马纹交替背景色（白色与极浅灰蓝）
            dataRow.setBackgroundColor((r % 2 == 0) ? Color.parseColor("#FFFFFF") : Color.parseColor("#F8FAFC"));
            dataRow.setLayoutParams(new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int c = 0; c < colCount; c++) {
                String cellText = (c < rowData.size()) ? rowData.get(c) : "";
                TextView tvCell = new TextView(context);
                tvCell.setText(MarkdownParser.parseInline(cellText));
                tvCell.setMovementMethod(LinkMovementMethod.getInstance());
                tvCell.setTextColor(Color.parseColor("#1E293B"));
                tvCell.setTextSize(Math.max(13, fontSize - 3));
                tvCell.setLineSpacing(0, 1.15f);
                tvCell.setPadding(dp12, dp8, dp12, dp8);
                tvCell.setGravity(Gravity.CENTER_VERTICAL);
                tvCell.setMinEms(3);
                dataRow.addView(tvCell);

                // 单元格垂直分割线
                if (c < colCount - 1) {
                    View vDivider = new View(context);
                    TableRow.LayoutParams vLp = new TableRow.LayoutParams(dp1, ViewGroup.LayoutParams.MATCH_PARENT);
                    vDivider.setLayoutParams(vLp);
                    vDivider.setBackgroundColor(Color.parseColor("#E2E8F0"));
                    dataRow.addView(vDivider);
                }
            }
            tableLayout.addView(dataRow);

            // 行间水平分割线
            if (r < tableData.rows.size() - 1) {
                View rowDivider = new View(context);
                rowDivider.setLayoutParams(new TableLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp1));
                rowDivider.setBackgroundColor(Color.parseColor("#F1F5F9"));
                tableLayout.addView(rowDivider);
            }
        }

        cardContainer.addView(tableLayout);
        hsv.addView(cardContainer);
        return hsv;
    }
}
