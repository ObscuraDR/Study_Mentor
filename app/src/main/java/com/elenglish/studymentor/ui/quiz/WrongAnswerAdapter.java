package com.elenglish.studymentor.ui.quiz;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemWrongAnswerBinding;
import com.elenglish.studymentor.domain.model.WrongAnswer;

import java.util.ArrayList;
import java.util.List;

final class WrongAnswerAdapter extends RecyclerView.Adapter<WrongAnswerAdapter.Holder> {
    interface Listener {
        void onRetryQuiz(WrongAnswer item);
    }

    private final List<WrongAnswer> items = new ArrayList<>();
    private final Listener listener;

    WrongAnswerAdapter(Listener listener) {
        this.listener = listener;
    }

    void submitList(List<WrongAnswer> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWrongAnswerBinding.inflate(
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
        private final ItemWrongAnswerBinding binding;

        Holder(ItemWrongAnswerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WrongAnswer item) {
            binding.wrongQuizTitle.setText(item.getQuizTitle());
            binding.wrongPrompt.setText(item.getPrompt());
            binding.wrongSelected.setText(binding.getRoot().getContext().getString(
                    R.string.wrong_answer_selected, item.getSelectedOptionText()));
            binding.wrongCorrect.setText(binding.getRoot().getContext().getString(
                    R.string.wrong_answer_correct, item.getCorrectOptionText()));
            binding.wrongCount.setText(binding.getRoot().getResources().getQuantityString(
                    R.plurals.wrong_answer_count, item.getWrongCount(), item.getWrongCount()));
            binding.wrongRetryQuiz.setOnClickListener(v -> listener.onRetryQuiz(item));
        }
    }
}
