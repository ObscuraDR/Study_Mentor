package com.elenglish.studymentor.ui.learning;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class TopicsActivity extends AppCompatActivity {

    private static final String EXTRA_SUBJECT_ID = "subjectId";

    public static Intent createIntent(Context context, String subjectId) {
        Intent intent = new Intent(context, TopicsActivity.class);
        intent.putExtra(EXTRA_SUBJECT_ID, subjectId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String subjectId = getIntent().getStringExtra(EXTRA_SUBJECT_ID);
        TopicsFragment fragment = TopicsFragment.newInstance(subjectId);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }
}
