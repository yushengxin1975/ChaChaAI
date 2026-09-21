package com.chacha.ai;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.List;

public class ChatAdapter extends BaseAdapter {
    private Context context;
    private List<ChatMessage> messages;
    private int fontSize = 22;

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
        TextView tvRole = (TextView) convertView.findViewById(R.id.tv_role);
        TextView tvTime = (TextView) convertView.findViewById(R.id.tv_time);
        TextView tvContent = (TextView) convertView.findViewById(R.id.tv_content);

        tvTime.setText(msg.time);
        tvContent.setText(msg.content);
        tvContent.setTextSize(fontSize);
        tvRole.setTextSize(Math.max(13, fontSize - 4));
        tvTime.setTextSize(Math.max(10, fontSize - 8));

        if (msg.role == ChatMessage.ROLE_USER) {
            tvRole.setText("[我]");
            tvRole.setTextColor(Color.parseColor("#4FC3F7"));
        } else if (msg.role == ChatMessage.ROLE_AI) {
            tvRole.setText("[AI]");
            tvRole.setTextColor(Color.parseColor("#81C784"));
        } else {
            tvRole.setText("[系统]");
            tvRole.setTextColor(Color.parseColor("#FFB74D"));
        }

        return convertView;
    }
}
