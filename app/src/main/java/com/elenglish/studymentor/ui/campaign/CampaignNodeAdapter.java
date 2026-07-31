package com.elenglish.studymentor.ui.campaign;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.elenglish.studymentor.R;
import com.elenglish.studymentor.databinding.ItemCampaignNodeBinding;
import com.elenglish.studymentor.domain.model.CampaignLessonNode;
import com.elenglish.studymentor.domain.model.CampaignLessonState;
import com.elenglish.studymentor.domain.model.CampaignProjection;
import com.elenglish.studymentor.domain.model.CampaignTopicNode;
import com.elenglish.studymentor.domain.model.CampaignZone;

import java.util.ArrayList;
import java.util.List;

final class CampaignNodeAdapter extends RecyclerView.Adapter<CampaignNodeAdapter.Holder> {
    interface Listener {
        void onLessonSelected(String lessonId);
    }

    private static final int TYPE_ZONE = 0;
    private static final int TYPE_TOPIC = 1;
    private static final int TYPE_LESSON = 2;
    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    CampaignNodeAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(CampaignProjection campaign) {
        rows.clear();
        for (CampaignZone zone : campaign.getZones()) {
            rows.add(new Row(TYPE_ZONE, zone.getName(),
                    zone.getCompletedLessons() + "/" + zone.getTotalLessons(), null, null));
            for (CampaignTopicNode topic : zone.getTopics()) {
                rows.add(new Row(TYPE_TOPIC, topic.getName(),
                        topic.getCompletedLessons() + "/" + topic.getTotalLessons(), null, null));
                for (CampaignLessonNode lesson : topic.getLessons()) {
                    rows.add(new Row(TYPE_LESSON, lesson.getTitle(), null,
                            lesson.getLessonId(), lesson.getState()));
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCampaignNodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemCampaignNodeBinding binding;

        Holder(ItemCampaignNodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            binding.campaignNodeTitle.setText(row.title);
            binding.campaignNodeMeta.setVisibility(View.GONE);
            binding.campaignNodeState.setVisibility(View.GONE);
            binding.getRoot().setOnClickListener(null);
            binding.getRoot().setClickable(false);
            int start = row.type == TYPE_ZONE ? 0
                    : row.type == TYPE_TOPIC ? binding.getRoot().getResources()
                    .getDimensionPixelSize(R.dimen.spacing_md)
                    : binding.getRoot().getResources().getDimensionPixelSize(R.dimen.spacing_xl);
            binding.campaignNodeContent.setPadding(start, 0, 0, 0);
            binding.campaignNodeTitle.setTypeface(null,
                    row.type == TYPE_LESSON ? Typeface.NORMAL : Typeface.BOLD);
            if (row.meta != null) {
                binding.campaignNodeMeta.setText(row.meta);
                binding.campaignNodeMeta.setVisibility(View.VISIBLE);
            }
            if (row.type == TYPE_LESSON) {
                binding.campaignNodeState.setVisibility(View.VISIBLE);
                binding.campaignNodeState.setText(labelFor(row.state));
                boolean available = row.state != CampaignLessonState.Unavailable;
                binding.getRoot().setEnabled(available);
                binding.getRoot().setClickable(available);
                if (available) {
                    binding.getRoot().setOnClickListener(v ->
                            listener.onLessonSelected(row.lessonId));
                }
            }
        }

        private int labelFor(CampaignLessonState state) {
            if (state == CampaignLessonState.Completed) return R.string.campaign_lesson_completed;
            if (state == CampaignLessonState.Recommended) return R.string.campaign_lesson_recommended;
            if (state == CampaignLessonState.NotStarted) return R.string.campaign_lesson_not_started;
            return R.string.campaign_lesson_unavailable;
        }
    }

    private static final class Row {
        final int type;
        final String title;
        final String meta;
        final String lessonId;
        final CampaignLessonState state;

        Row(int type, String title, String meta, String lessonId, CampaignLessonState state) {
            this.type = type;
            this.title = title;
            this.meta = meta;
            this.lessonId = lessonId;
            this.state = state;
        }
    }
}
