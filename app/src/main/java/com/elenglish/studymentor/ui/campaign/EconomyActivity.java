package com.elenglish.studymentor.ui.campaign;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ActivityEconomyBinding;
import com.elenglish.studymentor.domain.model.InventoryItem;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EconomyActivity extends AppCompatActivity {
    private ActivityEconomyBinding binding;
    private EconomyViewModel viewModel;
    private ShopItemAdapter adapter;

    public static Intent createIntent(Context context) {
        return new Intent(context, EconomyActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEconomyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(EconomyViewModel.class);
        adapter = new ShopItemAdapter(itemId -> viewModel.purchase(itemId));
        binding.shopRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.shopRecycler.setAdapter(adapter);
        binding.economyRetry.setOnClickListener(v -> viewModel.load());
        binding.purchaseRetry.setOnClickListener(v -> viewModel.retryPurchase());
        viewModel.getUiStateLiveData().observe(this, this::render);
    }

    private void render(EconomyUiState state) {
        binding.economyLoading.setVisibility(View.GONE);
        binding.economyContent.setVisibility(View.GONE);
        binding.economyError.setVisibility(View.GONE);
        if (state instanceof EconomyUiState.Loading) {
            binding.economyLoading.setVisibility(View.VISIBLE);
        } else if (state instanceof EconomyUiState.Failed) {
            binding.economyError.setVisibility(View.VISIBLE);
        } else if (state instanceof EconomyUiState.Content) {
            EconomyUiState.Content content = (EconomyUiState.Content) state;
            binding.economyContent.setVisibility(View.VISIBLE);
            binding.economyBalance.setText(getString(
                    R.string.economy_balance, content.getEconomy().getBalance()));
            binding.inventorySummary.setText(inventorySummary(
                    content.getEconomy().getInventory()));
            String submitting = content.getPurchase() instanceof PurchaseState.Submitting
                    ? ((PurchaseState.Submitting) content.getPurchase()).getItemId() : null;
            adapter.submit(content.getEconomy().getShopItems(),
                    content.getEconomy().getBalance(), submitting);
            binding.purchaseRetry.setVisibility(View.GONE);
            if (content.getPurchase() instanceof PurchaseState.Success) {
                binding.purchaseStatus.setText(R.string.economy_purchase_success);
            } else if (content.getPurchase() instanceof PurchaseState.Failed) {
                PurchaseState.Failed failed = (PurchaseState.Failed) content.getPurchase();
                binding.purchaseStatus.setText(R.string.economy_purchase_failed);
                binding.purchaseRetry.setVisibility(
                        failed.getRetryable() ? View.VISIBLE : View.GONE);
            } else if (content.getPurchase() instanceof PurchaseState.Submitting) {
                binding.purchaseStatus.setText(R.string.economy_buying);
            } else {
                binding.purchaseStatus.setText(R.string.economy_cosmetic_only);
            }
        }
    }

    private String inventorySummary(List<InventoryItem> inventory) {
        if (inventory.isEmpty()) return getString(R.string.economy_inventory_empty);
        List<String> rows = new ArrayList<>();
        for (InventoryItem item : inventory) {
            rows.add(getString(R.string.economy_inventory_item,
                    item.getItemId(), item.getQuantity()));
        }
        return android.text.TextUtils.join("\n", rows);
    }
}
