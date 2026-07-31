package com.elenglish.studymentor.ui.flashcards;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemFlashcardDeckBinding;
import com.elenglish.studymentor.domain.model.FlashcardDeck;

public final class FlashcardDeckAdapter
        extends ListAdapter<FlashcardDeck, FlashcardDeckAdapter.ViewHolder> {

    interface OnDeckClickListener { void onClick(FlashcardDeck deck); }

    private final OnDeckClickListener listener;

    FlashcardDeckAdapter(OnDeckClickListener listener) {
        super(new DiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemFlashcardDeckBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlashcardDeck deck = getItem(position);
        holder.binding.deckName.setText(deck.getName());
        String description = deck.getDescription();
        holder.binding.deckDescription.setVisibility(
                description == null || description.trim().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.deckDescription.setText(description);
        holder.binding.deckCount.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.flashcards_card_count, deck.getCardCount(), deck.getCardCount()));
        holder.itemView.setOnClickListener(v -> listener.onClick(deck));
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ItemFlashcardDeckBinding binding;
        ViewHolder(ItemFlashcardDeckBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class DiffCallback extends DiffUtil.ItemCallback<FlashcardDeck> {
        @Override public boolean areItemsTheSame(FlashcardDeck oldItem, FlashcardDeck newItem) {
            return oldItem.getId().equals(newItem.getId());
        }
        @Override public boolean areContentsTheSame(FlashcardDeck oldItem, FlashcardDeck newItem) {
            return oldItem.equals(newItem);
        }
    }
}
