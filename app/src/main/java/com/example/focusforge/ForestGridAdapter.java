package com.example.focusforge;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.List;

public class ForestGridAdapter extends BaseAdapter {
    private final Context context;
    private final List<HistoryModel> list;

    public ForestGridAdapter(Context context, List<HistoryModel> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() { return list.size(); }

    @Override
    public Object getItem(int position) { return list.get(position); }

    @Override
    public long getItemId(int position) { return list.get(position).getId(); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_forest_tile, parent, false);
        }

        HistoryModel item = list.get(position);
        TextView tvEmoji = convertView.findViewById(R.id.tvTileEmoji);
        TextView tvType = convertView.findViewById(R.id.tvTileType);
        TextView tvDate = convertView.findViewById(R.id.tvTileDate);

        tvDate.setText(item.getDate().length() > 10 ? item.getDate().substring(5, 10) : item.getDate());

        switch (item.getTreeType()) {
            case "OAK":
                tvEmoji.setText("🌳");
                tvType.setText("Oak (" + item.getDuration() + "m)");
                tvType.setTextColor(Color.parseColor("#1B5E20"));
                break;
            case "PINE":
                tvEmoji.setText("🌲");
                tvType.setText("Pine (" + item.getDuration() + "m)");
                tvType.setTextColor(Color.parseColor("#2E7D32"));
                break;
            case "WITHERED":
                tvEmoji.setText("🥀");
                tvType.setText("Dead Stump");
                tvType.setTextColor(Color.parseColor("#D32F2F"));
                break;
            default:
                tvEmoji.setText("🌿");
                tvType.setText("Sprout (" + item.getDuration() + "m)");
                tvType.setTextColor(Color.parseColor("#388E3C"));
                break;
        }

        return convertView;
    }
}