// src/main/java/com/example/letsdoit/MemberAdapter.java
package com.example.letsdoit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    // NEW INTERFACE
    public interface MemberDeleteListener {
        void onMemberSelectionChanged(User user, boolean isSelected);
    }

    private List<User> memberList;
    private Set<User> selectedMembers; // To track selected members
    private MemberDeleteListener listener;
    private boolean isDeleteMode = false; // State to control checkbox visibility

    // UPDATED Constructor
    public MemberAdapter(List<User> memberList, Set<User> selectedMembers, MemberDeleteListener listener) {
        this.memberList = memberList;
        this.selectedMembers = selectedMembers;
        this.listener = listener;
    }

    // NEW: Toggle delete mode and notify data change
    public void toggleDeleteMode(boolean isDeleteMode) {
        this.isDeleteMode = isDeleteMode;
        notifyDataSetChanged();
    }

    public boolean isDeleteMode() {
        return isDeleteMode;
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

        // Serial Number is position + 1
        holder.tvSerialNumber.setText(String.valueOf(position + 1) + ".");

        // Display Email
        holder.tvMemberEmail.setText(user.getEmail() != null ? user.getEmail() : "N/A");

        // Display Password
        holder.tvMemberPassword.setText(user.getPassword() != null ? user.getPassword() : "N/A");

        // Handle Checkbox visibility and state
        if (isDeleteMode) {
            holder.cbMemberSelect.setVisibility(View.VISIBLE);
            holder.cbMemberSelect.setChecked(selectedMembers.contains(user));

            holder.cbMemberSelect.setOnCheckedChangeListener(null); // Clear listener to prevent unwanted calls during binding

            holder.cbMemberSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onMemberSelectionChanged(user, isChecked);
                }
            });

            // Make the whole item clickable to select/deselect
            holder.itemView.setOnClickListener(v -> holder.cbMemberSelect.setChecked(!holder.cbMemberSelect.isChecked()));

        } else {
            holder.cbMemberSelect.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null); // Disable item click when not in delete mode
        }
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvSerialNumber, tvMemberEmail, tvMemberPassword;
        CheckBox cbMemberSelect; // NEW

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSerialNumber = itemView.findViewById(R.id.tv_serial_number);
            tvMemberEmail = itemView.findViewById(R.id.tv_member_email);
            tvMemberPassword = itemView.findViewById(R.id.tv_member_password);
            cbMemberSelect = itemView.findViewById(R.id.cb_member_select); // NEW
        }
    }
}