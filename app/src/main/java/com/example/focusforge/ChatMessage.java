package com.example.focusforge;

// Holds one chat bubble - either a user message or a bot reply
public class ChatMessage {
    private final String message; // the text content
    private final boolean isUser; // true = user sent it, false = bot sent it

    public ChatMessage(String message, boolean isUser) {
        this.message = message;
        this.isUser = isUser;
    }

    public String getMessage() { return message; } // used by ChatAdapter
    public boolean isUser() { return isUser; } // decides bubble side/color
}