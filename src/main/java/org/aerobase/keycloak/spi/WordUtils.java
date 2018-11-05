package org.aerobase.keycloak.spi;

public class WordUtils {

    public static String uncapitalize(final String str) {
        return uncapitalize(str, null);
    }

	public static String uncapitalize(final String str, final char... delimiters) {
		final int delimLen = delimiters == null ? -1 : delimiters.length;
		if ((str == null || str.length() == 0) || delimLen == 0) {
			return str;
		}
		final char[] buffer = str.toCharArray();
		boolean uncapitalizeNext = true;
		for (int i = 0; i < buffer.length; i++) {
			final char ch = buffer[i];
			if (isDelimiter(ch, delimiters)) {
				uncapitalizeNext = true;
			} else if (uncapitalizeNext) {
				buffer[i] = Character.toLowerCase(ch);
				uncapitalizeNext = false;
			}
		}
		return new String(buffer);
	}

	/**
	 * Is the character a delimiter.
	 *
	 * @param ch         the character to check
	 * @param delimiters the delimiters
	 * @return true if it is a delimiter
	 */
	private static boolean isDelimiter(final char ch, final char[] delimiters) {
		if (delimiters == null) {
			return Character.isWhitespace(ch);
		}
		for (final char delimiter : delimiters) {
			if (ch == delimiter) {
				return true;
			}
		}
		return false;
	}
}
