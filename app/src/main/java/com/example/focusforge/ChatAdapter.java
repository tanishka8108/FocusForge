package com.example.focusforge;

import android.content.Context;
import android.view.Gravity;                                    // Controls left/right alignment of a view within its parent
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

// Same Adapter pattern as ForestAdapter, but this one turns ChatMessage
// objects into chat bubbles for the AI Study Buddy conversation.
public class ChatAdapter extends ArrayAdapter<ChatMessage> {

    public ChatAdapter(Context context, ArrayList<ChatMessage> messages) {
        super(context, 0, messages);                              // 0 = we inflate our own row layout manually below
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {                                // No recycled view available yet
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_chat_bubble, parent, false);
        }

        ChatMessage item = getItem(position);                     // Get this row's data object
        if (item == null) return convertView;                     // Safety check

        // Grab this row's two widgets: the outer container (for alignment)
        // and the inner text view (for the bubble content + color)
        LinearLayout layoutContainer = convertView.findViewById(R.id.chatBubbleContainer);
        TextView tvMsg = convertView.findViewById(R.id.tvChatMessage);

        tvMsg.setText(item.getMessage());                         // Set the actual message text

        if (item.isUser()) {                                      // This message was sent BY the user
            layoutContainer.setGravity(Gravity.END);              // Push the bubble to the right side
            tvMsg.setBackgroundResource(R.drawable.bg_bubble_user); // Use the "user" bubble background shape/color
            tvMsg.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        } else {                                                  // This message came FROM the bot
            layoutContainer.setGravity(Gravity.START);            // Push the bubble to the left side
            tvMsg.setBackgroundResource(R.drawable.bg_bubble_bot); // Use the "bot" bubble background shape/color
            tvMsg.setTextColor(ContextCompat.getColor(getContext(), R.color.text_soft));
        }

        return convertView;                                       // Hand the finished bubble back to the ListView
    }
}