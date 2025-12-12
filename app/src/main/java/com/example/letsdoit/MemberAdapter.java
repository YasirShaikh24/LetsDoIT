// src/main/java/com/example/letsdoit/MemberAdapter.java
package com.example.letsdoit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    // MODIFIED INTERFACE: Added onMemberEditClick
    public interface MemberDeleteListener {
        void onMemberDeleteClick(User user);
        void onMemberEditClick(User user);
    }

    private List<User> memberList;
    private MemberDeleteListener listener;

    // isDeleteMode, selectedMembers removed

    // UPDATED Constructor
    public MemberAdapter(List<User> memberList, MemberDeleteListener listener) {
        this.memberList = memberList;
        this.listener = listener;
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

        // UI UPDATE: Set Serial Number/Badge Text
        holder.tvSerialNumberBadge.setText(String.valueOf(position + 1));

        // UI UPDATE: Display Name (New field)
        holder.tvMemberDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User " + String.valueOf(position + 1));

        // Display Email
        holder.tvMemberEmail.setText(user.getEmail() != null ? user.getEmail() : "N/A");

        // Display Password (UNMASKED - using actual password from user object)
        holder.tvMemberPassword.setText(user.getPassword() != null ? user.getPassword() : "N/A");

        // Attach listener to the delete button
        holder.btnDeleteMember.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberDeleteClick(user);
            }
        });

        // Attach listener to the new edit button
        holder.btnEditMember.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberEditClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvSerialNumberBadge, tvMemberDisplayName, tvMemberEmail, tvMemberPassword;
        ImageButton btnDeleteMember, btnEditMember;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            // Updated IDs for new layout
            tvSerialNumberBadge = itemView.findViewById(R.id.tv_serial_number_badge);
            tvMemberDisplayName = itemView.findViewById(R.id.tv_member_display_name);
            tvMemberEmail = itemView.findViewById(R.id.tv_member_email);
            tvMemberPassword = itemView.findViewById(R.id.tv_member_password);

            btnDeleteMember = itemView.findViewById(R.id.btn_delete_member);
            btnEditMember = itemView.findViewById(R.id.btn_edit_member);
        }
    }
}