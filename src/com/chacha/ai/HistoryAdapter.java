package com.chacha.ai;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.util.List;

public class HistoryAdapter extends BaseAdapter {
    public interface OnHistoryActionListener {
        void onSelectSession(ChatSession session);
        void onDeleteSession(ChatSession session);
    }

    private Context context;
    private List<ChatSession> sessions;
    private OnHistoryActionListener listener;

    public HistoryAdapter(Context context, List<ChatSession> sessions, OnHistoryActionListener listener) {
        this.context = context;
        this.sessions = sessions;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return sessions.size();
    }

    @Override
    public Object getItem(int position) {
        return sessions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        }

        final ChatSession item = sessions.get(position);
        TextView tvTitle = (TextView) convertView.findViewById(R.id.tv_session_title);
        TextView tvTime = (TextView) convertView.findViewById(R.id.tv_session_time);
        Button btnDelete = (Button) convertView.findViewById(R.id.btn_delete_session);
        View layoutClick = convertView.findViewById(R.id.layout_session_click);

        tvTitle.setText(item.title);
        tvTime.setText(item.time);

        layoutClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onSelectSession(item);
                }
            }
        });

        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onDeleteSession(item);
                }
            }
        });

        return convertView;
    }
}
