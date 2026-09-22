package com.devp.forge.doc;

import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * The inverse of {@link MarkdownParser}: front matter as YAML on top, body below.
 *
 * <p>Block style throughout, keys in insertion order, so a file the UI wrote reads like a
 * file a human wrote — and a Git diff of two versions shows the one field that changed.
 */
public final class MarkdownWriter {

	private MarkdownWriter() {
	}

	public static String write(Map<String, Object> frontMatter, String body) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		options.setIndent(2);
		options.setIndicatorIndent(2);
		options.setIndentWithIndicator(true);
		String yaml = new Yaml(options).dump(frontMatter);

		StringBuilder out = new StringBuilder("---\n").append(yaml);
		if (!yaml.endsWith("\n")) {
			out.append('\n');
		}
		out.append("---\n");
		if (body != null && !body.isBlank()) {
			out.append('\n').append(body.strip()).append('\n');
		}
		return out.toString();
	}
}
