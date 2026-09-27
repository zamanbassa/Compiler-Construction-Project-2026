import java.util.*;
import java.util.regex.*;

public class SPLLexer {
    enum TokenType {
        NUM,
        USER_DEFINED_NAME,
        STRING,
        UNKNOWN
    }

    record Token(TokenType type, String value) {// key value pairing
        @Override public String toString() {
            return String.format("[%-s : \"%s\"]", type, value);
        }
    }

    // Patterns

    // Blank = space or newline (per the spec)
    private static final String B = "[ \\n]";

    // NUM: 4 cases from the spec
    private static final String NUM =
        "0" + B                                          //0_
        + "|-?0\\.[0-9]*[1-9]" + B                      //-0.33_
        + "|-?[1-9][0-9]*\\.[0-9]*[1-9]" + B            //-3.14_
        + "|-?[1-9][0-9]*" + B;                          //-42_

    private static final String USER_DEFINED_NAME =
        "#[0-9a-z]*" + B;//start with # and end with blank


    private static final String STRING =
        "\"[-,.:\\?!0-9a-z]*\"" + B;//start with " and end with "

    private static final Pattern LEXER = Pattern.compile(
        "(" + NUM + ")"    // group 1
        + "|(" + USER_DEFINED_NAME + ")" // group 2
        + "|(" + STRING + ")"    // group 3
    );

    // ── Tokenizer ────────────────────────────────────────────────
    public List<Token> tokenize(String input) {
        Matcher m = LEXER.matcher(input); //Matcher will use the Pattern to tokenize 
        List<Token> tokens = new ArrayList<>();
        int lastEnd = 0;

        while (m.find()) {//iterate through each char of string //false when no more input
            // Catch any unrecognised characters between matches
            if (m.start() != lastEnd) {
                String unknown = input.substring(lastEnd, m.start());//this string you gave, we do not know it.
                tokens.add(new Token(TokenType.UNKNOWN, unknown));
            }

            // The matched value — strip the trailing blank before storing
            String raw   = m.group();
            String value = raw.substring(0, raw.length() - 1);//remove the blank

            if(m.group(1) != null){
                tokens.add(new Token(TokenType.NUM, value));
            }
            else if(m.group(2) != null){
                tokens.add(new Token(TokenType.USER_DEFINED_NAME, value));
            }
            else if(m.group(3) != null){
                tokens.add(new Token(TokenType.STRING, value));
            }

            lastEnd = m.end();
        }

        if (lastEnd != input.length()) {
            tokens.add(new Token(TokenType.UNKNOWN, input.substring(lastEnd)));
        }

        return tokens;
    }

    // Quick test 
    public static void main(String[] args) {
        SPLLexer lexer = new SPLLexer();

        String input = "42 -3.14 0 #myvar \"hello,world\" 007 ";//007 not allowed, leading 0s


        System.out.println("Input: " + input);
        System.out.println("─".repeat(45));
        lexer.tokenize(input).forEach(System.out::println);
    }
}