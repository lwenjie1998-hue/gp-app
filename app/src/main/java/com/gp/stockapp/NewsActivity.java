package com.gp.stockapp;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gp.stockapp.adapter.NewsAdapter;
import com.gp.stockapp.model.StockNews;
import com.gp.stockapp.repository.StockRepository;

import java.util.List;

public class NewsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NewsAdapter adapter;
    private StockRepository stockRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news);

        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("市场要闻");
        }

        // 初始化 Repository
        stockRepository = StockRepository.getInstance(this);

        // 初始化视图
        recyclerView = findViewById(R.id.recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 加载数据
        loadNews();

        // 下拉刷新
        swipeRefreshLayout.setOnRefreshListener(this::loadNews);
    }

    private void loadNews() {
        swipeRefreshLayout.setRefreshing(true);
        new Thread(() -> {
            // 获取最新 50 条新闻
            List<StockNews> newsList = stockRepository.getLatestNews(50);
            
            // UI更新
            runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                if (newsList != null && !newsList.isEmpty()) {
                    adapter = new NewsAdapter(newsList);
                    recyclerView.setAdapter(adapter);
                } else {
                    Toast.makeText(NewsActivity.this, "暂无新闻数据", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}