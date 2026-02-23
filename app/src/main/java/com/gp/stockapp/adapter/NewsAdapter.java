package com.gp.stockapp.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

        // 显示推荐股票
        if (news.hasRecommendedStocks()) {
            holder.layoutRecommendedStocks.setVisibility(View.VISIBLE);
            holder.tvRecommendedStocks.setText(news.getRecommendedStocks());
        } else {
            holder.layoutRecommendedStocks.setVisibility(View.GONE);
        }

        // 显示重大新闻标记
        if (news.isHighImpact()) {
            holder.tvImpactTag.setVisibility(View.VISIBLE);
            holder.tvImpactTag.setText("重大");
            // 设置红色圆角背景
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setCornerRadius(8f);
            tagBg.setColor(0xFFD32F2F); // 红色
            holder.tvImpactTag.setBackground(tagBg);
            // 标题也加粗高亮
            holder.tvTitle.setTextColor(0xFFD32F2F);
        } else if (news.getImportance() >= 3) {
            holder.tvImpactTag.setVisibility(View.VISIBLE);
            holder.tvImpactTag.setText("关注");
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setCornerRadius(8f);
            tagBg.setColor(0xFFFF9800); // 橙色
            holder.tvImpactTag.setBackground(tagBg);
            holder.tvTitle.setTextColor(0xFF333333);
        } else {
            holder.tvImpactTag.setVisibility(View.GONE);
            holder.tvTitle.setTextColor(0xFF333333);
        }
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSummary, tvSource, tvTime;
        LinearLayout layoutRecommendedStocks;
        TextView tvRecommendedStocks;
        TextView tvImpactTag;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            tvSource = itemView.findViewById(R.id.tv_source);
            tvTime = itemView.findViewById(R.id.tv_time);
            layoutRecommendedStocks = itemView.findViewById(R.id.layout_recommended_stocks);
            tvRecommendedStocks = itemView.findViewById(R.id.tv_recommended_stocks);
            tvImpactTag = itemView.findViewById(R.id.tv_impact_tag);
        }
    }
}