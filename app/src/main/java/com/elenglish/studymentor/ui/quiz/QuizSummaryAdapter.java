package com.elenglish.studymentor.ui.quiz;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemQuizSummaryBinding;
import com.elenglish.studymentor.domain.model.QuizSummary;

public class QuizSummaryAdapter extends ListAdapter<QuizSummary, QuizSummaryAdapter.ViewHolder> {

    private final OnItemClickListener listener;

    public QuizSummaryAdapter(OnItemClickListener listener) {
        super(new DiffCallback());
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemQuizSummaryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuizSummary q = getItem(position);
        holder.binding.quizItemTitle.setText(q.getTitle());
        holder.binding.quizItemCount.setText(holder.itemView.getResources().getQuantityString(
                R.plurals.quiz_question_count, q.getQuestionCount(), q.getQuestionCount()));
        holder.itemView.setOnClickListener(v -> listener.onClick(q));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemQuizSummaryBinding binding;
        ViewHolder(ItemQuizSummaryBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }

    interface OnItemClickListener {
        void onClick(QuizSummary quiz);
    }

    static class DiffCallback extends DiffUtil.ItemCallback<QuizSummary> {
        @Override public boolean areItemsTheSame(QuizSummary a, QuizSummary b) { return a.getId().equals(b.getId()); }
        @Override public boolean areContentsTheSame(QuizSummary a, QuizSummary b) { return a.getTitle().equals(b.getTitle()); }
    }
}
