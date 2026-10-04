package com.example.focusforge;

import android.content.Context;
import android.view.LayoutInflater;                            // Turns an XML layout file into real View objects
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;                             // Base class for "list of objects -> list of rows"
import android.widget.TextView;

import androidx.annotation.NonNull;                              // Marks a parameter/return as "never null"
import androidx.annotation.Nullable;                              // Marks a parameter/return as "might be null"
import androidx.core.content.ContextCompat;                       // Safe way to fetch color resources across Android versions

import java.util.ArrayList;

// This class is the "translator" between our data (a list of ForestSession
// objects) and what the ListView actually needs (a View to draw for each row).
public class ForestAdapter extends ArrayAdapter<ForestSession> {   // <ForestSession> = this adapter handles that specific type

    // Constructor: just hands everything up to the parent ArrayAdapter class.
    // The "0" means "no built-in layout resource" - we inflate our own layout manually below instead.
    public ForestAdapter(Context context, ArrayList<ForestSession> sessions) {
        super(context, 0, sessions);
    }

    // Called by the ListView once per visible row, asking "give me a View to draw here."
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {                                // convertView = a recycled old row view, if Android has one to reuse
            // No recyclable view available - inflate a brand new one from our XML layout
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_forest_session, parent, false);
            // "false" means don't attach it to the parent yet - the ListView itself handles that
        }

        ForestSession session = getItem(position);                // Get the data object for this specific row's position
        if (session == null) return convertView;                  // Safety check - return early if somehow null

        // Find each widget inside THIS row's layout (not the whole screen -
        // convertView here refers to just this one row's inflated XML)
        TextView tvIcon = convertView.findViewById(R.id.tvSessionIcon);
        TextView tvSubject = convertView.findViewById(R.id.tvSessionSubject);
        TextView tvDetails = convertView.findViewById(R.id.tvSessionDetails);
        TextView tvBadge = convertView.findViewById(R.id.tvSessionBadge);

        tvSubject.setText(session.getStatus());                   // e.g. "Success (Coding)"
        tvDetails.setText(session.getDuration() + " mins • +" + session.getXp() + " XP"); // String concatenation building the detail line

        // Decide the icon, badge text, and badge color based on whether this session succeeded
        if (session.getStatus().toLowerCase().contains("success")) {
            tvIcon.setText("🌲");                                  // Full tree emoji for a successful session
            tvBadge.setText("SAVED");
            tvBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.success)); // Green badge text
        } else {
            tvIcon.setText("🥀");                                  // Wilted flower emoji for a failed session
            tvBadge.setText("WITHERED");
            tvBadge.setTextColor(ContextCompat.getColor(getContext(), R.color.danger));  // Red badge text
        }

        return convertView;                                       // Hand the finished, filled-in row back to the ListView
    }
}