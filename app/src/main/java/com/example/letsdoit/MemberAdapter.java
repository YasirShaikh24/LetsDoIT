// src/main/java/com/example/letsdoit/MemberAdapter.java
package com.example.letsdoit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

    private List<User> memberList;

    public MemberAdapter(List<User> memberList) {
        this.memberList = memberList;
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
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvSerialNumber, tvMemberEmail, tvMemberPassword;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSerialNumber = itemView.findViewById(R.id.tv_serial_number);
            tvMemberEmail = itemView.findViewById(R.id.tv_member_email);
            tvMemberPassword = itemView.findViewById(R.id.tv_member_password);
        }
    }
}