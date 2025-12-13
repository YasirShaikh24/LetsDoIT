// src/main/java/com/example/letsdoit/MemberAdapter.java
package com.example.letsdoit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    public interface MemberActionListener {
        void onMemberDeleteClick(User user);
        void onMemberEditClick(User user);
        void onMemberSelectClick(User user);
    }

    private List<User> memberList;
    private MemberActionListener listener;
    private boolean isSelectionMode = false;
    private Set<String> selectedIds = new HashSet<>();

    public MemberAdapter(List<User> memberList, MemberActionListener listener) {
        this.memberList = memberList;
        this.listener = listener;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
    }

    public void setSelectedIds(Set<String> selectedIds) {
        this.selectedIds = selectedIds;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        User user = memberList.get(position);

        holder.tvSerialNumberBadge.setText(String.valueOf(position + 1));
        holder.tvMemberDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User " + String.valueOf(position + 1));
        holder.tvMemberEmail.setText(user.getEmail() != null ? user.getEmail() : "N/A");
        holder.tvMemberPassword.setText(user.getPassword() != null ? user.getPassword() : "N/A");

        boolean isSelected = selectedIds.contains(user.getDocumentId());
        holder.checkboxSelect.setChecked(isSelected);

        if (isSelectionMode) {
            // In selection mode, show the checkbox and hide action buttons and badge
            holder.checkboxSelect.setVisibility(View.VISIBLE);
            holder.tvSerialNumberBadge.setVisibility(View.GONE); // IMPORTANT: Hide badge
            holder.btnEditMember.setVisibility(View.GONE);
            holder.btnDeleteMember.setVisibility(View.GONE);
        } else {
            // In normal mode, hide the checkbox and show action buttons and badge
            holder.checkboxSelect.setVisibility(View.GONE);
            holder.tvSerialNumberBadge.setVisibility(View.VISIBLE); // IMPORTANT: Show badge
            holder.btnEditMember.setVisibility(View.VISIBLE);
            holder.btnDeleteMember.setVisibility(View.VISIBLE);
        }

        // --- Action Button Listeners (Normal Mode) ---
        holder.btnDeleteMember.setOnClickListener(v -> {
            if (!isSelectionMode && listener != null) {
                listener.onMemberDeleteClick(user);
            }
        });

        holder.btnEditMember.setOnClickListener(v -> {
            if (!isSelectionMode && listener != null) {
                listener.onMemberEditClick(user);
            }
        });

        // --- Selection Logic Listeners ---
        // 1. Checkbox click
        holder.checkboxSelect.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberSelectClick(user);
            }
        });

        // 2. Item View click (Always call onMemberSelectClick to initiate selection mode)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberSelectClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        // We need to keep a reference to the badge even though it's inside a FrameLayout in the XML
        TextView tvSerialNumberBadge, tvMemberDisplayName, tvMemberEmail, tvMemberPassword;
        ImageButton btnDeleteMember, btnEditMember;
        CheckBox checkboxSelect;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSerialNumberBadge = itemView.findViewById(R.id.tv_serial_number_badge);
            tvMemberDisplayName = itemView.findViewById(R.id.tv_member_display_name);
            tvMemberEmail = itemView.findViewById(R.id.tv_member_email);
            tvMemberPassword = itemView.findViewById(R.id.tv_member_password);
            btnDeleteMember = itemView.findViewById(R.id.btn_delete_member);
            btnEditMember = itemView.findViewById(R.id.btn_edit_member);
            checkboxSelect = itemView.findViewById(R.id.checkbox_select);
        }
    }
}