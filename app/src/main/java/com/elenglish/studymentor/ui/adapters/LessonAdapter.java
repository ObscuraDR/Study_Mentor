package com.elenglish.studymentor.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemLessonBinding;
import com.elenglish.studymentor.domain.model.Difficulty;
import com.elenglish.studymentor.domain.model.Lesson;

/**
 * RecyclerView adapter for Lesson list.
 * Shows completion status, title, duration tag, difficulty tag, and description.
 */
public class LessonAdapter extends ListAdapter<LessonAdapter.LessonItem, LessonAdapter.ViewHolder> {

    private final OnItemClickListener listener;

    public LessonAdapter(OnItemClickListener listener) {
        super(new LessonDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLessonBinding binding = ItemLessonBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LessonItem item = getItem(position);
        holder.bind(item, listener);
    }

    /**
     * A lesson + completion flag paired for the adapter.
     */
    public static class LessonItem {
        private final Lesson lesson;
        private final boolean isCompleted;

        public LessonItem(Lesson lesson, boolean isCompleted) {
            this.lesson = lesson;
            this.isCompleted = isCompleted;
        }

        public Lesson getLesson() { return lesson; }
        public boolean isCompleted() { return isCompleted; }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLessonBinding binding;

        ViewHolder(ItemLessonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LessonItem item, OnItemClickListener listener) {
            Lesson lesson = item.getLesson();
            binding.itemTitle.setText(lesson.getTitle());
            binding.itemDescription.setText(lesson.getDescription());
            binding.itemDescription.setVisibility(
                    lesson.getDescription() != null && !lesson.getDescription().isEmpty()
                            ? View.VISIBLE : View.GONE);

            // Duration chip
            binding.chipDuration.setText(
                    binding.getRoot().getContext().getString(
                            R.string.lesson_estimated_minutes, lesson.getEstimatedMinutes()));

            // Difficulty chip
            Difficulty difficulty = lesson.getDifficulty();
            if (difficulty != null) {
                binding.chipDifficulty.setVisibility(View.VISIBLE);
                int labelRes;
                if (difficulty == Difficulty.Beginner) {
                    labelRes = R.string.profile_education_beginner;
                } else if (difficulty == Difficulty.Intermediate) {
                    labelRes = R.string.profile_education_intermediate;
                } else {
                    labelRes = R.string.profile_education_advanced;
                }
                binding.chipDifficulty.setText(labelRes);
            } else {
                binding.chipDifficulty.setVisibility(View.GONE);
            }

            // Completion icon
            if (item.isCompleted()) {
                binding.itemCompletionIcon.setImageResource(R.drawable.ic_check_circle);
                binding.itemCompletionIcon.setImageTintList(
                        binding.getRoot().getContext()
                                .getResources().getColorStateList(R.color.feedback_success));
            } else {
                binding.itemCompletionIcon.setImageResource(R.drawable.ic_school);
                // Tint is set by XML
            }

            binding.getRoot().setOnClickListener(v -> listener.onLessonClick(lesson));
        }
    }

    public interface OnItemClickListener {
        void onLessonClick(Lesson lesson);
    }

    private static class LessonDiffCallback extends DiffUtil.ItemCallback<LessonItem> {
        @Override
        public boolean areItemsTheSame(@NonNull LessonItem oldItem, @NonNull LessonItem newItem) {
            return oldItem.getLesson().getId().equals(newItem.getLesson().getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull LessonItem oldItem, @NonNull LessonItem newItem) {
            Lesson o = oldItem.getLesson();
            Lesson n = newItem.getLesson();
            return oldItem.isCompleted() == newItem.isCompleted()
                    && o.getTitle().equals(n.getTitle())
                    && o.getDisplayOrder() == n.getDisplayOrder();
        }
    }
}
