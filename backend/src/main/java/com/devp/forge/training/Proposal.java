package com.devp.forge.training;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the coach proposed, and what it cost.
 *
 * @param prompt    the new system prompt; {@code null} when the answer had no usable prompt block
 * @param rationale the coach's reasons, one line per change
 * @param raw       the full answer, kept for the step
 */
public record Proposal(String prompt, String rationale, String raw, String model, Integer tokens, long durationMs) {

	private static final Pattern OPEN = Pattern.compile("(?m)^\\s*<<<\\s*$");
	private static final Pattern CLOSE = Pattern.compile("(?m)^\\s*>>>\\s*$");
	private static final Pattern RATIONALE = Pattern.compile("(?is)(?:RATIONALE|BEGRUENDUNG|BEGR\u00dcNDUNG)\\s*:?(.*?)(?:^\\s*PROMPT\\s*:|<<<)",
			Pattern.MULTILINE);

	/**
	 * Lenient on the frame, strict on the core: the prompt is what stands between the first
	 * {@code <<<} line and the last {@code >>>} line. A prompt may contain its own headings and
	 * lists, so nothing inside is interpreted.
	 *
	 * <p>Models get the closing marker wrong now and then — a second {@code <<<} instead of
	 * {@code >>>}, or none at all. Throwing such an answer away wastes the coach call; the prompt
	 * is still there, and the gate measures it like any other.
	 */
	public static Proposal parse(String raw, String model, Integer tokens, long durationMs) {
		String text = raw == null ? "" : raw;
		String prompt = null;
		Matcher open = OPEN.matcher(text);
		if (open.find()) {
			int start = open.end();
			int end = lastLineAfter(CLOSE, text, start);
			if (end < 0) {
				int reopened = lastLineAfter(OPEN, text, start);
				end = reopened >= 0 ? reopened : text.length();
			}
			String inner = text.substring(start, end).strip();
			prompt = inner.isEmpty() ? null : inner;
		}
		Matcher r = RATIONALE.matcher(text);
		String rationale = r.find() ? r.group(1).strip() : null;
		if (rationale != null && rationale.isEmpty()) {
			rationale = null;
		}
		return new Proposal(prompt, rationale, text, model, tokens, durationMs);
	}

	/** Start of the last marker line after {@code from}, or -1. */
	private static int lastLineAfter(Pattern marker, String text, int from) {
		Matcher m = marker.matcher(text);
		int last = -1;
		while (m.find()) {
			if (m.start() >= from) {
				last = m.start();
			}
		}
		return last;
	}
}
