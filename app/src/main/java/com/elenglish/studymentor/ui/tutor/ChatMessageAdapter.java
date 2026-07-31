package com.elenglish.studymentor.ui.tutor;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.databinding.ItemChatMessageBinding;
import com.elenglish.studymentor.R;
import com.elenglish.studymentor.domain.model.TutorAnswerStatus;
import com.elenglish.studymentor.domain.model.TutorTurn;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.VH> {

    private List<TutorTurn> turns = new ArrayList<>();

    public void submitList(List<TutorTurn> turns) {
        this.turns = turns != null ? turns : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TutorTurn turn = turns.get(pos);
        if (turn instanceof TutorTurn.Question) {
            h.binding.senderLabel.setText(R.string.tutor_you);
            h.binding.messageText.setText(((TutorTurn.Question) turn).getText());
            h.binding.bubbleRow.setGravity(Gravity.END);
            h.binding.messageCard.setCardBackgroundColor(
                    h.itemView.getContext().getColor(R.color.primary_container));
            h.binding.answerStatus.setVisibility(View.GONE);
        } else {
            TutorTurn.Answer answer = (TutorTurn.Answer) turn;
            h.binding.senderLabel.setText(R.string.tutor_tutor);
            h.binding.messageText.setText(answer.getAnswer().getAnswer());
            h.binding.bubbleRow.setGravity(Gravity.START);
            h.binding.messageCard.setCardBackgroundColor(
                    h.itemView.getContext().getColor(R.color.light_surface_variant));
            TutorAnswerStatus status = answer.getAnswer().getStatus();
            if (status == TutorAnswerStatus.Refused) {
                h.binding.answerStatus.setText(R.string.tutor_refused);
                h.binding.answerStatus.setVisibility(View.VISIBLE);
            } else if (status == TutorAnswerStatus.Truncated) {
                h.binding.answerStatus.setText(R.string.tutor_truncated);
                h.binding.answerStatus.setVisibility(View.VISIBLE);
            } else {
                h.binding.answerStatus.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() { return turns.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ItemChatMessageBinding binding;
        VH(ItemChatMessageBinding b) { super(b.getRoot()); this.binding = b; }
    }
}
