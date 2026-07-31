package com.elenglish.studymentor.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.databinding.ItemTopicBinding;
import com.elenglish.studymentor.domain.model.Topic;

/**
 * RecyclerView adapter for Topic list.
 */
public class TopicAdapter extends ListAdapter<Topic, TopicAdapter.ViewHolder> {

    private final OnItemClickListener listener;

    public TopicAdapter(OnItemClickListener listener) {
        super(new TopicDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTopicBinding binding = ItemTopicBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Topic topic = getItem(position);
        holder.bind(topic, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemTopicBinding binding;

        ViewHolder(ItemTopicBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Topic topic, OnItemClickListener listener) {
            binding.itemName.setText(topic.getName());
            binding.getRoot().setOnClickListener(v -> listener.onTopicClick(topic));
        }
    }

    public interface OnItemClickListener {
        void onTopicClick(Topic topic);
    }

    private static class TopicDiffCallback extends DiffUtil.ItemCallback<Topic> {
        @Override
        public boolean areItemsTheSame(@NonNull Topic oldItem, @NonNull Topic newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Topic oldItem, @NonNull Topic newItem) {
            return oldItem.getName().equals(newItem.getName())
                    && oldItem.getDisplayOrder() == newItem.getDisplayOrder();
        }
    }
}
