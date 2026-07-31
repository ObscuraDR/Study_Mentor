package com.elenglish.studymentor.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.databinding.ItemSubjectBinding;
import com.elenglish.studymentor.domain.model.Subject;

/**
 * RecyclerView adapter for Subject list.
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
public class SubjectAdapter extends ListAdapter<Subject, SubjectAdapter.ViewHolder> {

    private final OnItemClickListener listener;

    public SubjectAdapter(OnItemClickListener listener) {
        super(new SubjectDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubjectBinding binding = ItemSubjectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subject subject = getItem(position);
        holder.bind(subject, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubjectBinding binding;

        ViewHolder(ItemSubjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Subject subject, OnItemClickListener listener) {
            binding.itemName.setText(subject.getName());
            binding.getRoot().setOnClickListener(v -> listener.onSubjectClick(subject));
        }
    }

    public interface OnItemClickListener {
        void onSubjectClick(Subject subject);
    }

    private static class SubjectDiffCallback extends DiffUtil.ItemCallback<Subject> {
        @Override
        public boolean areItemsTheSame(@NonNull Subject oldItem, @NonNull Subject newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Subject oldItem, @NonNull Subject newItem) {
            return oldItem.getName().equals(newItem.getName())
                    && oldItem.getDisplayOrder() == newItem.getDisplayOrder();
        }
    }
}
