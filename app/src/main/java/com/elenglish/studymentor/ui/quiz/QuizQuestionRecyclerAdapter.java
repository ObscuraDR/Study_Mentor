package com.elenglish.studymentor.ui.quiz;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemQuizQuestionBinding;
import com.elenglish.studymentor.domain.model.QuizAttemptResult;
import com.elenglish.studymentor.domain.model.QuizOption;
import com.elenglish.studymentor.domain.model.QuizQuestion;
import com.elenglish.studymentor.domain.model.QuizQuestionResult;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class QuizQuestionRecyclerAdapter extends RecyclerView.Adapter<QuizQuestionRecyclerAdapter.VH> {

    private List<QuizQuestion> questions;
    private QuizAttemptResult result;
    private final Map<String, String> selectedOptions = new HashMap<>();
    private final OnOptionListener listener;

    public QuizQuestionRecyclerAdapter(OnOptionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<QuizQuestion> questions) { this.questions = questions; notifyDataSetChanged(); }
    public void setResult(QuizAttemptResult result) { this.result = result; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQuizQuestionBinding b = ItemQuizQuestionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        QuizQuestion q = questions.get(pos);
        h.binding.questionPrompt.setText(q.getPrompt());
        RadioGroup rg = h.binding.optionsGroup;
        rg.removeAllViews();

        for (QuizOption opt : q.getOptions()) {
            RadioButton rb = new RadioButton(h.itemView.getContext());
            rb.setText(opt.getText());
            rb.setId(View.generateViewId());
            rb.setLayoutParams(new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rg.addView(rb);

            boolean isSelected = opt.getId().equals(selectedOptions.get(q.getId()));
            rb.setChecked(isSelected);
            rb.setMinHeight((int) (48 * h.itemView.getResources().getDisplayMetrics().density));
            if (result != null) {
                QuizQuestionResult qr = findResult(result, q.getId());
                if (qr != null) {
                    rb.setEnabled(false);
                    if (qr.getCorrectOptionId().equals(opt.getId())) {
                        rb.setText(rb.getText() + " — " + h.itemView.getContext().getString(R.string.quiz_answer_correct));
                        rb.setTextColor(h.itemView.getContext().getColor(R.color.feedback_success));
                    } else if (qr.getSelectedOptionId().equals(opt.getId())) {
                        rb.setText(rb.getText() + " — " + h.itemView.getContext().getString(R.string.quiz_answer_wrong));
                        rb.setTextColor(h.itemView.getContext().getColor(R.color.feedback_error));
                    }
                }
            } else {
                rb.setOnClickListener(v -> {
                    selectedOptions.put(q.getId(), opt.getId());
                    listener.onSelect(q.getId(), opt.getId());
                });
            }
        }
    }

    @Override public int getItemCount() { return questions != null ? questions.size() : 0; }

    private QuizQuestionResult findResult(QuizAttemptResult r, String qId) {
        for (QuizQuestionResult qr : r.getQuestionResults()) {
            if (qr.getQuestionId().equals(qId)) return qr;
        }
        return null;
    }

    static class VH extends RecyclerView.ViewHolder {
        ItemQuizQuestionBinding binding;
        VH(ItemQuizQuestionBinding b) { super(b.getRoot()); this.binding = b; }
    }

    interface OnOptionListener {
        void onSelect(String questionId, String optionId);
    }
}
