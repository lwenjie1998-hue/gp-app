package com.gp.stockapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<StockNews> newsList;
    private final SimpleDateFormat sdf;

    public NewsAdapter(List<StockNews> newsList) {
        this.newsList = newsList;
        this.sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        StockNews news = newsList.get(position);
        
        holder.tvTitle.setText(news.getTitle());
        holder.tvSummary.setText(news.getSummary() != null ? news.getSummary() : news.getContent());
        holder.tvSource.setText(news.getSource() != null ? news.getSource() : "来源未知");
        
        if (news.getPublishTime() > 0) {
            holder.tvTime.setText(sdf.format(new Date(news.getPublishTime())));
        } else {
            holder.tvTime.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSummary, tvSource, tvTime;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            tvSource = itemView.findViewById(R.id.tv_source);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}