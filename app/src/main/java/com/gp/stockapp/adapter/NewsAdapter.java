package com.gp.stockapp.adapter;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

        // 点击整条新闻弹窗显示具体内容
        holder.itemView.setOnClickListener(v -> showNewsDetailDialog(v, news));
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    /**
     * 弹窗显示新闻详细内容
     */
    private void showNewsDetailDialog(View view, StockNews news) {
        android.content.Context context = view.getContext();

        // 构建弹窗内容
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(context, 16);
        contentLayout.setPadding(pad, pad, pad, pad);

        // 标题
        TextView tvTitle = new TextView(context);
        tvTitle.setText(news.getTitle());
        tvTitle.setTextColor(0xFF212121);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTypeface(null, Typeface.BOLD);
        contentLayout.addView(tvTitle);

        // 来源 + 时间
        StringBuilder meta = new StringBuilder();
        if (news.getSource() != null && !news.getSource().isEmpty()) {
            meta.append(news.getSource());
        }
        if (news.getPublishTime() > 0) {
            if (meta.length() > 0) meta.append("  |  ");
            meta.append(sdf.format(new Date(news.getPublishTime())));
        }
        if (meta.length() > 0) {
            TextView tvMeta = new TextView(context);
            tvMeta.setText(meta.toString());
            tvMeta.setTextColor(0xFF999999);
            tvMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metaParams.topMargin = dpToPx(context, 6);
            tvMeta.setLayoutParams(metaParams);
            contentLayout.addView(tvMeta);
        }

        // 分隔线
        View divider = new View(context);
        divider.setBackgroundColor(0xFFE0E0E0);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 1));
        divParams.topMargin = dpToPx(context, 10);
        divParams.bottomMargin = dpToPx(context, 10);
        divider.setLayoutParams(divParams);
        contentLayout.addView(divider);

        // 正文内容（优先显示content，其次summary）
        String contentText = "";
        if (news.getContent() != null && !news.getContent().isEmpty()) {
            contentText = news.getContent();
        } else if (news.getSummary() != null && !news.getSummary().isEmpty()) {
            contentText = news.getSummary();
        } else {
            contentText = "暂无详细内容";
        }
        TextView tvContent = new TextView(context);
        tvContent.setText(contentText);
        tvContent.setTextColor(0xFF424242);
        tvContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvContent.setLineSpacing(0, 1.4f);
        contentLayout.addView(tvContent);

        // 推荐股票
        if (news.hasRecommendedStocks()) {
            TextView tvStocks = new TextView(context);
            tvStocks.setText("📈 相关股票：" + news.getRecommendedStocks());
            tvStocks.setTextColor(0xFF1976D2);
            tvStocks.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LinearLayout.LayoutParams stockParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stockParams.topMargin = dpToPx(context, 10);
            tvStocks.setLayoutParams(stockParams);
            contentLayout.addView(tvStocks);
        }

        // 包裹在ScrollView中
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(contentLayout);

        new AlertDialog.Builder(context)
                .setView(scrollView)
                .setPositiveButton("关闭", null)
                .show();
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