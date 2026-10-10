package com.wafgateway.worklet1.tokenizer;

import com.wafgateway.common.Token;
import com.wafgateway.common.TokenType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonTokenizer: maximal-munch tokenizing, exact positions/values,
 * error reporting, and linear-time behavior on deeply nested input.
 */
class JsonTokenizerTest {

    private static JsonTokenizer newTokenizer() throws Exception {
        return new JsonTokenizer(10_000);
    }

    private static List<Token> tokenize(String json, int maxTokens) throws Exception {
        return newTokenizer().tokenize(json.getBytes(StandardCharsets.UTF_8), maxTokens);
    }

    // ========================================================================
    // Exact token sequence and positions
    // ========================================================================

    @Test
    void testSimpleObjectExactTokensAndPositions() throws Exception {
        List<Token> tokens = tokenize("{\"user\":\"bob\",\"age\":25}", 1000);

        TokenType[] expectedTypes = {
                TokenType.LBRACE, TokenType.STRING, TokenType.COLON, TokenType.STRING,
                TokenType.COMMA, TokenType.STRING, TokenType.COLON, TokenType.NUMBER, TokenType.RBRACE
        };
        int[] expectedPositions = {0, 1, 7, 8, 13, 14, 19, 20, 22};

        assertEquals(expectedTypes.length, tokens.size());
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], tokens.get(i).type(), "token " + i + " type");
            assertEquals(expectedPositions[i], tokens.get(i).position(), "token " + i + " position");
        }

        assertEquals("\"user\"", tokens.get(1).value());
        assertEquals("\"bob\"", tokens.get(3).value());
        assertEquals("\"age\"", tokens.get(5).value());
        assertEquals("25", tokens.get(7).value());
    }

    @Test
    void testNestedStructure() throws Exception {
        List<Token> tokens = tokenize("{\"a\":[1,{\"b\":true}]}", 1000);
        assertEquals(13, tokens.size());

        TokenType[] expectedTypes = {
                TokenType.LBRACE, TokenType.STRING, TokenType.COLON, TokenType.LBRACKET,
                TokenType.NUMBER, TokenType.COMMA, TokenType.LBRACE, TokenType.STRING,
                TokenType.COLON, TokenType.TRUE, TokenType.RBRACE, TokenType.RBRACKET, TokenType.RBRACE
        };
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], tokens.get(i).type(), "token " + i);
        }
    }

    @Test
    void testBracketsInsideStringAreNotSeparateTokens() throws Exception {
        List<Token> tokens = tokenize("{\"msg\":\"hello [world] {ok}\"}", 1000);
        assertEquals(5, tokens.size());

        assertEquals(TokenType.LBRACE, tokens.get(0).type());
        assertEquals(TokenType.STRING, tokens.get(1).type());
        assertEquals(TokenType.COLON, tokens.get(2).type());
        assertEquals(TokenType.STRING, tokens.get(3).type());
        assertEquals("\"hello [world] {ok}\"", tokens.get(3).value());
        assertEquals(TokenType.RBRACE, tokens.get(4).type());
    }

    @Test
    void testEscapedQuoteInString() throws Exception {
        List<Token> tokens = tokenize("{\"q\":\"a\\\"b\"}", 1000);
        assertEquals(5, tokens.size());
        Token stringToken = tokens.get(3);
        assertEquals(TokenType.STRING, stringToken.type());
        // The raw bytes are: " a \ " b " -> value includes the surrounding
        // quotes and the literal backslash-quote escape sequence, unprocessed.
        assertEquals("\"a\\\"b\"", stringToken.value());
    }

    // ========================================================================
    // Numbers and keywords
    // ========================================================================

    @Test
    void testNumberFormats() throws Exception {
        String[] numbers = {"0", "-3", "3.14", "1e10", "-2.5E-3"};
        for (String n : numbers) {
            List<Token> tokens = tokenize(n, 10);
            assertEquals(1, tokens.size(), "input=" + n);
            assertEquals(TokenType.NUMBER, tokens.get(0).type(), "input=" + n);
            assertEquals(n, tokens.get(0).value(), "input=" + n);
        }
    }

    @Test
    void testKeywords() throws Exception {
        List<Token> trueTokens = tokenize("true", 10);
        assertEquals(1, trueTokens.size());
        assertEquals(TokenType.TRUE, trueTokens.get(0).type());

        List<Token> falseTokens = tokenize("false", 10);
        assertEquals(1, falseTokens.size());
        assertEquals(TokenType.FALSE, falseTokens.get(0).type());

        List<Token> nullTokens = tokenize("null", 10);
        assertEquals(1, nullTokens.size());
        assertEquals(TokenType.NULL, nullTokens.get(0).type());
    }

    // ========================================================================
    // Whitespace skipping
    // ========================================================================

    @Test
    void testWhitespaceAndNewlinesSkipped() throws Exception {
        List<Token> tokens = tokenize("{ \"a\" : 1 ,\n \"b\" : 2 }", 1000);
        TokenType[] expectedTypes = {
                TokenType.LBRACE, TokenType.STRING, TokenType.COLON, TokenType.NUMBER,
                TokenType.COMMA, TokenType.STRING, TokenType.COLON, TokenType.NUMBER, TokenType.RBRACE
        };
        assertEquals(expectedTypes.length, tokens.size());
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], tokens.get(i).type(), "token " + i);
        }
    }

    // ========================================================================
    // Errors
    // ========================================================================

    @Test
    void testInvalidTokenAtExactPosition() throws Exception {
        TokenizeException ex = assertThrows(TokenizeException.class, () -> tokenize("{\"a\": @}", 1000));
        assertEquals(TokenizeException.Reason.INVALID_TOKEN, ex.reason());
        assertEquals(6, ex.position());
    }

    @Test
    void testUnterminatedStringIsInvalidToken() throws Exception {
        TokenizeException ex = assertThrows(TokenizeException.class, () -> tokenize("{\"unterminated", 1000));
        assertEquals(TokenizeException.Reason.INVALID_TOKEN, ex.reason());
    }

    @Test
    void testIncompleteKeywordIsInvalidToken() throws Exception {
        TokenizeException ex = assertThrows(TokenizeException.class, () -> tokenize("tru", 1000));
        assertEquals(TokenizeException.Reason.INVALID_TOKEN, ex.reason());
        assertEquals(0, ex.position());
    }

    // ========================================================================
    // No nesting validation (Worklet 2's job)
    // ========================================================================

    @Test
    void testMismatchedBracketsStillTokenizeFine() throws Exception {
        List<Token> tokens = tokenize("{\"a\":[1,2}", 1000);
        assertEquals(8, tokens.size());
        assertEquals(TokenType.LBRACE, tokens.get(0).type());
        assertEquals(TokenType.LBRACKET, tokens.get(3).type());
        assertEquals(TokenType.RBRACE, tokens.get(7).type());
        // No RBRACKET anywhere - mismatched, but this tokenizer doesn't care.
        assertTrue(tokens.stream().noneMatch(t -> t.type() == TokenType.RBRACKET));
    }

    // ========================================================================
    // maxTokens
    // ========================================================================

    @Test
    void testTooManyTokensThrows() throws Exception {
        // Exactly 9 tokens: { "a" : 1 , "b" : 2 }
        String body = "{\"a\":1,\"b\":2}";
        assertEquals(9, tokenize(body, 1000).size());

        TokenizeException ex = assertThrows(TokenizeException.class, () -> tokenize(body, 5));
        assertEquals(TokenizeException.Reason.TOO_MANY_TOKENS, ex.reason());
    }

    @Test
    void testExactlyMaxTokensDoesNotThrow() throws Exception {
        // Exactly 5 tokens: { "a" : 1 }
        String body = "{\"a\":1}";
        List<Token> tokens = tokenize(body, 5);
        assertEquals(5, tokens.size());
    }

    // ========================================================================
    // Performance: deep nesting in linear time
    // ========================================================================

    @Test
    void testDeeplyNestedBracketsTokenizeInLinearTime() throws Exception {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            deep.append('[');
        }
        for (int i = 0; i < 5000; i++) {
            deep.append(']');
        }

        long start = System.currentTimeMillis();
        List<Token> tokens = tokenize(deep.toString(), 20_000);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(10_000, tokens.size());
        assertTrue(elapsed < 1000, "tokenizing took " + elapsed + "ms, expected well under 1000ms");
    }
}
