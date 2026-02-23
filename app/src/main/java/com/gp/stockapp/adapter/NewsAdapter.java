package com.gp.stockapp.adapter;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.R;
import com.gp.stockapp.utils.StockAppHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // 显示重大新闻标记（在标题前面）
        if (news.isHighImpact()) {
            holder.tvImpactTag.setVisibility(View.VISIBLE);
            holder.tvImpactTag.setText("重大");
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setCornerRadius(6f);
            tagBg.setColor(0xFFD32F2F);
            holder.tvImpactTag.setBackground(tagBg);
            holder.tvTitle.setTextColor(0xFFD32F2F);
        } else if (news.getImportance() >= 3) {
            holder.tvImpactTag.setVisibility(View.VISIBLE);
            holder.tvImpactTag.setText("关注");
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setCornerRadius(6f);
            tagBg.setColor(0xFFFF9800);
            holder.tvImpactTag.setBackground(tagBg);
            holder.tvTitle.setTextColor(0xFF333333);
        } else {
            holder.tvImpactTag.setVisibility(View.GONE);
            holder.tvTitle.setTextColor(0xFF333333);
        }

        // 显示推荐股票（解析并设置点击事件）
        if (news.hasRecommendedStocks()) {
            holder.layoutRecommendedStocks.setVisibility(View.VISIBLE);
            renderRecommendedStocks(holder.stockTagsContainer, news.getRecommendedStocks());
        } else {
            holder.layoutRecommendedStocks.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    /**
     * 渲染推荐股票为可点击的标签
     * 格式："贵州茅台(600519)、五粮液(000858)"
     */
    private void renderRecommendedStocks(FlexboxLayout container, String recommendedStocks) {
        container.removeAllViews();
        if (recommendedStocks == null || recommendedStocks.trim().isEmpty()) return;

        // 正则匹配：股票名称(代码)
        Pattern pattern = Pattern.compile("([^(、]+)\\(([0-9]{6})\\)");
        Matcher matcher = pattern.matcher(recommendedStocks);

        while (matcher.find()) {
            String stockName = matcher.group(1);
            String stockCode = matcher.group(2);

            TextView tvTag = new TextView(container.getContext());
            tvTag.setText(stockName);
            tvTag.setTextColor(0xFF1976D2);
            tvTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvTag.setBackgroundResource(R.drawable.tag_bg_light);
            int padding = dpToPx(container.getContext(), 6);
            int paddingV = dpToPx(container.getContext(), 2);
            tvTag.setPadding(padding, paddingV, padding, paddingV);

            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagParams.rightMargin = dpToPx(container.getContext(), 6);
            tagParams.bottomMargin = dpToPx(container.getContext(), 4);
            tvTag.setLayoutParams(tagParams);

            // 点击跳转到同花顺
            String finalStockCode = stockCode;
            String finalStockName = stockName;
            tvTag.setOnClickListener(v -> {
                StockAppHelper.openInTongHuaShun(v.getContext(), finalStockCode, finalStockName);
            });

            container.addView(tvTag);
        }
    }

    private int dpToPx(android.content.Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSummary, tvSource, tvTime;
        LinearLayout layoutRecommendedStocks;
        FlexboxLayout stockTagsContainer;
        TextView tvImpactTag;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            tvSource = itemView.findViewById(R.id.tv_source);
            tvTime = itemView.findViewById(R.id.tv_time);
            layoutRecommendedStocks = itemView.findViewById(R.id.layout_recommended_stocks);
            stockTagsContainer = itemView.findViewById(R.id.stock_tags_container);
            tvImpactTag = itemView.findViewById(R.id.tv_impact_tag);
        }
    }
}