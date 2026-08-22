package com.inevitables.blehelper.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.inevitables.blehelper.R;
import com.inevitables.blehelper.mesh.BleMeshManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

    public static class LogItem {
        public final long timestamp;
        public final String tag;
        public final String message;
        public final int level;

        public LogItem(String tag, String message, int level) {
            this.timestamp = System.currentTimeMillis();
            this.tag = tag;
            this.message = message;
            this.level = level;
        }

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        public String getLevelLabel() {
            switch (level) {
                case BleMeshManager.LOG_SUCCESS:
                    return "[SUCCESS]";
                case BleMeshManager.LOG_WARN:
                    return "[WARN]";
                case BleMeshManager.LOG_ERROR:
                    return "[ERROR]";
                case BleMeshManager.LOG_RX:
                    return "[MESH RX]";
                case BleMeshManager.LOG_TX:
                    return "[MESH TX]";
                case BleMeshManager.LOG_INFO:
                default:
                    return "[INFO]";
            }
        }
    }

    private final List<LogItem> mLogs = new ArrayList<>();
    private static final int MAX_LOGS = 600;

    public void addLog(String tag, String message, int level) {
        mLogs.add(new LogItem(tag, message, level));
        if (mLogs.size() > MAX_LOGS) {
            mLogs.remove(0);
            notifyItemRemoved(0);
        }
        notifyItemInserted(mLogs.size() - 1);
    }

    public void clear() {
        mLogs.clear();
        notifyDataSetChanged();
    }

    public String getAllLogsAsText() {
        StringBuilder sb = new StringBuilder();
        for (LogItem item : mLogs) {
            sb.append(item.getFormattedTime())
                    .append(" ")
                    .append(item.getLevelLabel())
                    .append(" [")
                    .append(item.tag)
                    .append("] ")
                    .append(item.message)
                    .append("\n");
        }
        return sb.toString();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogItem item = mLogs.get(position);
        holder.tvTime.setText(item.getFormattedTime());
        holder.tvTag.setText(item.getLevelLabel());
        holder.tvMessage.setText(item.message);

        int colorRes;
        switch (item.level) {
            case BleMeshManager.LOG_SUCCESS:
                colorRes = R.color.log_text_success;
                break;
            case BleMeshManager.LOG_WARN:
                colorRes = R.color.log_text_warn;
                break;
            case BleMeshManager.LOG_ERROR:
                colorRes = R.color.log_text_error;
                break;
            case BleMeshManager.LOG_RX:
                colorRes = R.color.log_text_rx;
                break;
            case BleMeshManager.LOG_TX:
                colorRes = R.color.log_text_tx;
                break;
            case BleMeshManager.LOG_INFO:
            default:
                colorRes = R.color.log_text_info;
                break;
        }

        holder.tvTag.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));
    }

    @Override
    public int getItemCount() {
        return mLogs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvTag, tvMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_log_time);
            tvTag = itemView.findViewById(R.id.tv_log_tag);
            tvMessage = itemView.findViewById(R.id.tv_log_message);
        }
    }
}
