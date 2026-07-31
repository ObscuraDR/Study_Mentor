package com.elenglish.studymentor.ui.campaign;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemShopProductBinding;
import com.elenglish.studymentor.domain.model.ShopItem;

import java.util.ArrayList;
import java.util.List;

final class ShopItemAdapter extends RecyclerView.Adapter<ShopItemAdapter.Holder> {
    interface Listener {
        void onBuy(String itemId);
    }

    private final List<ShopItem> items = new ArrayList<>();
    private final Listener listener;
    private int balance;
    private String submittingItemId;

    ShopItemAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<ShopItem> newItems, int balance, String submittingItemId) {
        items.clear();
        items.addAll(newItems);
        this.balance = balance;
        this.submittingItemId = submittingItemId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemShopProductBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemShopProductBinding binding;

        Holder(ItemShopProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ShopItem item) {
            binding.shopItemName.setText(item.getName());
            binding.shopItemDescription.setText(item.getDescription());
            binding.shopItemPrice.setText(binding.getRoot().getContext().getString(
                    R.string.economy_price, item.getPriceShells()));
            if (item.getOwned()) {
                binding.shopBuy.setText(R.string.economy_owned);
            } else if (item.getId().equals(submittingItemId)) {
                binding.shopBuy.setText(R.string.economy_buying);
            } else {
                binding.shopBuy.setText(R.string.economy_buy);
            }
            boolean canBuy = item.getAvailable() && !item.getOwned()
                    && item.getPriceShells() <= balance && submittingItemId == null;
            binding.shopBuy.setEnabled(canBuy);
            binding.shopBuy.setVisibility(item.getAvailable() ? View.VISIBLE : View.GONE);
            binding.shopBuy.setOnClickListener(v -> listener.onBuy(item.getId()));
        }
    }
}
