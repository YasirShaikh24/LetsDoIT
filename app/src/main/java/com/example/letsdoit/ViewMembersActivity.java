// src/main/java/com/example/letsdoit/ViewMembersActivity.java
package com.example.letsdoit;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ViewMembersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MemberAdapter memberAdapter;
    private List<User> memberList;
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView tvEmptyState;

    private static final String TAG = "ViewMembersActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_members);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Team Members");
        }

        db = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.recycler_view_members);
        progressBar = findViewById(R.id.progress_bar);
        tvEmptyState = findViewById(R.id.tv_empty_state);

        memberList = new ArrayList<>();
        memberAdapter = new MemberAdapter(memberList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // Add a divider for better table visibility
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                layoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);

        recyclerView.setAdapter(memberAdapter);

        loadMembers();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadMembers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        // Load all documents from the "users" collection (regular members)
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    memberList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            memberList.add(user);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user: " + document.getId(), e);
                        }
                    }

                    loadAdmins();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading members", e);
                    progressBar.setVisibility(View.GONE);
                    tvEmptyState.setText("Error loading members: " + e.getMessage());
                    tvEmptyState.setVisibility(View.VISIBLE);
                });
    }

    // Separately load admins
    private void loadAdmins() {
        db.collection("admins")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            User user = document.toObject(User.class);
                            if (user != null && !memberList.stream().anyMatch(m -> m.getEmail().equals(user.getEmail()))) {
                                memberList.add(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing admin: " + document.getId(), e);
                        }
                    }

                    progressBar.setVisibility(View.GONE);

                    if (memberList.isEmpty()) {
                        tvEmptyState.setText("No members found.");
                        tvEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        memberAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading admins", e);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not fully load team list.", Toast.LENGTH_SHORT).show();
                });
    }
}