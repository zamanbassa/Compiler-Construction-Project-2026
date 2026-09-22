package spl.lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import spl.contract.ILexer;

public final class Lexer implements ILexer {
    private static final Map<String, TokenType> KEYWORDS = createKeywords();

    private String source;
    private int index;
    private int line;
    private int column;

    @Override
    public List<Token> tokenize(String input) {
        Objects.requireNonNull(input, "input");

        source = input;
        index = 0;
        line = 1;
        column = 1;

        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipBlankSpaces();
            if (atEnd()) {
                tokens.add(new Token(TokenType.EOF, "", line, column));
                return tokens;
            }
            tokens.add(readToken());
        }
    }

    private Token readToken() {
        int tokenLine = line;
        int tokenColumn = column;
        char first = current();

        if (first == '#') {
            return readUserDefinedName(tokenLine, tokenColumn);
        }
        if (first == '"') {
            return readString(tokenLine, tokenColumn);
        }
        if (isDigit(first) || first == '-') {
            return readNumber(tokenLine, tokenColumn);
        }
        if (isLetter(first)) {
            return readKeyword(tokenLine, tokenColumn);
        }

        if (isPunctuation(first)) {
            String lexeme = String.valueOf(first);
            advance();
            requireBlankAfterToken(lexeme, tokenLine, tokenColumn);
            return new Token(TokenType.SYMBOL, lexeme, tokenLine, tokenColumn);
        }

        throw error("unexpected character " + printable(first), tokenLine, tokenColumn);
    }

    /** USER-DEFINED-NAME: #(0-9|a-z)* followed by a blank space */
    private Token readUserDefinedName(int tokenLine, int tokenColumn) {
        int start = index;
        advance(); // '#'

        while (!atEnd() && (isDigit(current()) || isLowercaseLetter(current()))) {
            advance();
        }

        if (atEnd() || !isBlank(current())) {
            throw error("a user-defined name must be followed by a blank space",
                    tokenLine, tokenColumn);
        }

        String lexeme = source.substring(start, index);
        requireBlankAfterToken(lexeme, tokenLine, tokenColumn);
        return new Token(TokenType.IDENTIFIER, lexeme, tokenLine, tokenColumn);
    }

    /** STRING: "(,|.|:|-|?|!|0-9|a-z)*"_ */
    private Token readString(int tokenLine, int tokenColumn) {
        int start = index;
        advance(); // opening quote

        while (!atEnd() && current() != '"') {
            if (!isStringCharacter(current())) {
                throw error("invalid character inside a string: " + printable(current()),
                        line, column);
            }
            advance();
        }

        if (atEnd()) {
            throw error("unterminated string; expected a closing quote", tokenLine, tokenColumn);
        }
        advance(); // closing quote

        String lexeme = source.substring(start, index);
        requireBlankAfterToken(lexeme, tokenLine, tokenColumn);
        return new Token(TokenType.STRING, lexeme, tokenLine, tokenColumn);
    }

    /** NUM: one of the four numeric forms specified in the practical. */
    private Token readNumber(int tokenLine, int tokenColumn) {
        int start = index;
        while (!atEnd() && !isBlank(current())) {
            advance();
        }

        String lexeme = source.substring(start, index);
        if (!isValidNumber(lexeme)) {
            throw error("invalid number '" + lexeme + "'", tokenLine, tokenColumn);
        }
        // Reaching a blank is part of readNumber's normal scan. At EOF it is
        // still an error because the SPL specification requires the blank.
        requireBlankAfterToken(lexeme, tokenLine, tokenColumn);
        return new Token(TokenType.NUMBER, lexeme, tokenLine, tokenColumn);
    }

    private Token readKeyword(int tokenLine, int tokenColumn) {
        int start = index;
        while (!atEnd() && !isBlank(current())) {
            advance();
        }

        String lexeme = source.substring(start, index);
        TokenType type = KEYWORDS.get(lexeme);
        if (type == null) {
            throw error("unknown word '" + lexeme + "'", tokenLine, tokenColumn);
        }
        requireBlankAfterToken(lexeme, tokenLine, tokenColumn);
        return new Token(type, lexeme, tokenLine, tokenColumn);
    }

    private void requireBlankAfterToken(String lexeme, int tokenLine, int tokenColumn) {
        if (atEnd()) {
            throw error("token '" + lexeme + "' must be followed by a blank space",
                    tokenLine, tokenColumn);
        }
        if (!isBlank(current())) {
            throw error("token '" + lexeme + "' must be followed by a blank space",
                    line, column);
        }
    }

    private void skipBlankSpaces() {
        while (!atEnd() && isBlank(current())) {
            if (current() == '\r') {
                advance();
                if (!atEnd() && current() == '\n') {
                    advanceWithoutNewlineAccounting();
                }
                line++;
                column = 1;
            } else if (current() == '\n') {
                advanceWithoutNewlineAccounting();
                line++;
                column = 1;
            } else {
                // Ordinary spaces do not start a new line, but they still
                // move the current column forward.
                advance();
            }
        }
    }

    private void advance() {
        if (current() == '\r' || current() == '\n') {
            // Newline accounting is handled by skipBlankSpaces. A token may never contain a newline
            index++;
            return;
        }
        index++;
        column++;
    }

    private void advanceWithoutNewlineAccounting() {
        index++;
    }

    private boolean isValidNumber(String value) {
        if (value.equals("0")) {
            return true;
        }

        int position = value.startsWith("-") ? 1 : 0;
        if (position == value.length()) {
            return false;
        }

        if (value.charAt(position) == '0') {
            // The only legal zero-based form is 0.<digits ending in 1-9>.
            return value.length() > position + 2
                    && value.charAt(position + 1) == '.'
                    && endsInNonZeroDigit(value)
                    && allDigitsOrDot(value, position + 2);
        }

        if (!isNonZeroDigit(value.charAt(position))) {
            return false;
        }

        int dot = value.indexOf('.', position);
        if (dot < 0) {
            return allDigits(value, position);
        }

        // Decimal form: a non-zero integer part, optional decimal digits,
        // and a final non-zero decimal digit.
        return dot > position
                && allDigits(value, position, dot)
                && dot + 1 < value.length()
                && endsInNonZeroDigit(value)
                && allDigits(value, dot + 1, value.length());
    }

    private boolean allDigits(String value, int start) {
        return allDigits(value, start, value.length());
    }

    private boolean allDigits(String value, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean allDigitsOrDot(String value, int start) {
        int dotCount = 0;
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '.') {
                dotCount++;
            } else if (!isDigit(character)) {
                return false;
            }
        }
        return dotCount == 0;
    }

    private boolean endsInNonZeroDigit(String value) {
        return !value.isEmpty() && isNonZeroDigit(value.charAt(value.length() - 1));
    }

    private boolean isPunctuation(char character) {
        return character == ':'
                || character == ';'
                || character == '('
                || character == ')'
                || character == '{'
                || character == '}'
                || character == '=';
    }

    private boolean isStringCharacter(char character) {
        return character == ','
                || character == '.'
                || character == ':'
                || character == '-'
                || character == '?'
                || character == '!'
                || isDigit(character)
                || isLowercaseLetter(character);
    }

    private boolean isBlank(char character) {
        return character == ' ' || character == '\r' || character == '\n';
    }

    private boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private boolean isNonZeroDigit(char character) {
        return character >= '1' && character <= '9';
    }

    private boolean isLowercaseLetter(char character) {
        return character >= 'a' && character <= 'z';
    }

    private boolean isLetter(char character) {
        return isLowercaseLetter(character) || (character >= 'A' && character <= 'Z');
    }

    private boolean atEnd() {
        return index >= source.length();
    }

    private char current() {
        return source.charAt(index);
    }

    private LexicalException error(String message, int errorLine, int errorColumn) {
        return new LexicalException(message, errorLine, errorColumn);
    }

    private String printable(char character) {
        return switch (character) {
            case '\t' -> "<tab>";
            case '\r' -> "<carriage return>";
            case '\n' -> "<newline>";
            default -> "'" + character + "'";
        };
    }

    private static Map<String, TokenType> createKeywords() {
        Map<String, TokenType> keywords = new HashMap<>();
        for (String keyword : new String[]{
                "void", "num", "return", "print", "nop", "comment",
                "if", "then", "else", "mod", "add", "sub", "mul", "div",
                "neg", "not", "and", "or", "eq", "larger", "lesser",
                "while", "until", "do"}) {
            keywords.put(keyword, TokenType.KEYWORD);
        }
        return Map.copyOf(keywords);
    }
}
