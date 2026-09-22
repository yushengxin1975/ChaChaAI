package com.chacha.ai;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class ChatAdapter extends BaseAdapter {
    private Context context;
    private List<ChatMessage> messages;
    private int fontSize = 18;

    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    public void setFontSize(int size) {
        this.fontSize = size;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Object getItem(int position) {
        return messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false);
        }

        ChatMessage msg = messages.get(position);
        View llBubbleContainer = convertView.findViewById(R.id.ll_bubble_container);
        TextView tvRole = (TextView) convertView.findViewById(R.id.tv_role);
        TextView tvTime = (TextView) convertView.findViewById(R.id.tv_time);
        LinearLayout llContentContainer = (LinearLayout) convertView.findViewById(R.id.ll_content_container);
        TextView tvContent = (TextView) convertView.findViewById(R.id.tv_content);

        tvTime.setText(msg.time);
        tvRole.setTextSize(Math.max(13, fontSize - 5));
        tvTime.setTextSize(Math.max(11, fontSize - 7));
        tvTime.setTextColor(Color.parseColor("#64748B"));

        int textColor = Color.parseColor("#0F172A");

        if (msg.role == ChatMessage.ROLE_USER) {
            if (llBubbleContainer != null) {
                llBubbleContainer.setBackgroundResource(R.drawable.bubble_user);
            }
            tvRole.setText("【我】");
            tvRole.setTextColor(Color.parseColor("#15803D"));
            tvRole.setBackgroundResource(R.drawable.badge_user);
            textColor = Color.parseColor("#0F172A");
        } else if (msg.role == ChatMessage.ROLE_AI) {
            if (llBubbleContainer != null) {
                llBubbleContainer.setBackgroundResource(R.drawable.bubble_ai);
            }
            tvRole.setText("【AI】");
            tvRole.setTextColor(Color.parseColor("#0284C7"));
            tvRole.setBackgroundResource(R.drawable.badge_ai);
            textColor = Color.parseColor("#0F172A");
        } else {
            if (llBubbleContainer != null) {
                llBubbleContainer.setBackgroundResource(R.drawable.bubble_system);
            }
            tvRole.setText("【系统】");
            tvRole.setTextColor(Color.parseColor("#D97706"));
            tvRole.setBackgroundResource(R.drawable.badge_system);
            textColor = Color.parseColor("#475569");
        }

        List<MarkdownParser.ContentBlock> blocks = msg.getBlocks();
        boolean hasMultipleBlocks = false;
        if (blocks != null && !blocks.isEmpty()) {
            for (MarkdownParser.ContentBlock b : blocks) {
                if (b.type == MarkdownParser.ContentBlock.TYPE_TABLE) {
                    hasMultipleBlocks = true;
                    break;
                }
            }
            if (blocks.size() > 1) {
                hasMultipleBlocks = true;
            }
        }

        if (!hasMultipleBlocks) {
            if (llContentContainer != null && llContentContainer.getChildCount() > 1) {
                llContentContainer.removeViews(1, llContentContainer.getChildCount() - 1);
            }
            if (tvContent != null) {
                tvContent.setVisibility(View.VISIBLE);
                tvContent.setText(msg.getFormatted());
                tvContent.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                tvContent.setTextSize(fontSize);
                tvContent.setTextColor(textColor);
            }
        } else {
            if (tvContent != null) {
                tvContent.setVisibility(View.GONE);
            }
            if (llContentContainer != null) {
                if (llContentContainer.getChildCount() > 1) {
                    llContentContainer.removeViews(1, llContentContainer.getChildCount() - 1);
                }
                for (MarkdownParser.ContentBlock block : blocks) {
                    if (block.type == MarkdownParser.ContentBlock.TYPE_TEXT) {
                        if (block.text != null && block.text.length() > 0) {
                            TextView tv = new TextView(context);
                            tv.setText(block.text);
                            tv.setTextSize(fontSize);
                            tv.setTextColor(textColor);
                            tv.setLineSpacing(0, 1.25f);
                            tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            lp.topMargin = 4;
                            lp.bottomMargin = 4;
                            tv.setLayoutParams(lp);
                            llContentContainer.addView(tv);
                        }
                    } else if (block.type == MarkdownParser.ContentBlock.TYPE_TABLE) {
                        View tableView = MarkdownTableRenderer.createTableView(context, block.table, fontSize);
                        llContentContainer.addView(tableView);
                    }
                }
            }
        }

        return convertView;
    }
}
