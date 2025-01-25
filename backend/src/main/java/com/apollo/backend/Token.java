package com.apollo.backend;

import java.util.*;
import com.rpl.rama.*;
import com.apollo.backend.modules.TrendsAndHashtags;

public class Token implements RamaSerializable {
    private static final HashSet<Character> linkBoundaryChars = new HashSet<>(Arrays.asList(
            // whitespace chars
            ' ', '\t', '\n', '\r', '\f', '\b',
            // special chars not allowed in URLs
            '`', '\'', '"', '(', ')', '[', ']', '{', '}', '<', '>'));
    private static final HashSet<Character> boundaryChars = new HashSet<>(Arrays.asList(
            // whitespace chars
            ' ', '\t', '\n', '\r', '\f', '\b',
            // special chars
            '!', '$', '%', '^', '&', '*', '?', '\\', '.', ',', '`', '\'', '"', ';',
            '|', '-', '+', '=', '(', ')', '[', ']', '{', '}', '<', '>'));

    public enum TokenKind {
        BOUNDARY,
        WORD,
        LINK,
        HASHTAG,
        MENTION,
        SPACE
    }

    public TokenKind kind;
    public String content;

    private static boolean isLink(String content) {
        return content.startsWith("http://") || content.startsWith("https://");
    }

    private static void finishToken(List<Token> tokens, Token token) {
        if (token.content.length() > 0)
            tokens.add(token);
    }

    public static List<Token> parseTokens(String content) {
        List<Token> tokens = new ArrayList<>();
        Token currentToken = new Token(TokenKind.BOUNDARY, "");

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            boolean linkParsing = currentToken.kind == TokenKind.LINK;
            Set<Character> chars = linkParsing ? linkBoundaryChars : boundaryChars;

            // All special tokens now follow the same pattern
            if (!linkParsing && ch == 's' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.SPACE, "");
                i++;
            }
            // Hashtags - simplified like spaces
            else if (!linkParsing && ch == '#') {
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.HASHTAG, "");
            }
            // Mentions - simplified like spaces
            else if (!linkParsing && ch == '@') {
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.MENTION, "");
            }
            // Rest of the token handling...
            else if (chars.contains(ch)) {
                if (currentToken.kind == TokenKind.BOUNDARY)
                    currentToken.content += ch;
                else {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.BOUNDARY, String.valueOf(ch));
                }
            } else {
                if (currentToken.kind == TokenKind.BOUNDARY) {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.WORD, String.valueOf(ch));
                } else
                    currentToken.content += ch;
                if (currentToken.kind == TokenKind.WORD && isLink(currentToken.content))
                    currentToken.kind = TokenKind.LINK;
            }
        }
        finishToken(tokens, currentToken);
        return tokens;
    }

    public static Set<String> filterHashtags(List<Token> tokens) {
        Set<String> hashtags = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.HASHTAG)
                hashtags.add(token.content);
        }
        return hashtags;
    }

    public static Set<String> filterMentions(List<Token> tokens) {
        HashSet<String> mentions = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.MENTION)
                mentions.add(token.content);
        }
        return mentions;
    }

    public static Set<String> filterLinks(List<Token> tokens) {
        HashSet<String> urls = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.LINK)
                urls.add(token.content);
        }
        return urls;
    }

    public static Set<String> filterSpaces(List<Token> tokens) {
        HashSet<String> spaces = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.SPACE)
                spaces.add(token.content);
        }
        return spaces;
    }

    public Token(TokenKind kind, String content) {
        this.kind = kind;
        this.content = content;
    }

    public String getOrigContent() {
        if (kind == TokenKind.HASHTAG)
            return "#" + content;
        else if (kind == TokenKind.MENTION)
            return "@" + content;
        else if (kind == TokenKind.SPACE)
            return "s/" + content;
        else
            return content;
    }

    @Override
    public String toString() {
        return "Token{kind=" + kind + ", content='" + content + '\'' + '}';
    }
}