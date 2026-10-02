package com.devp.forge.training;

import java.util.List;
import java.util.UUID;

import com.devp.forge.doc.MarkdownParser;
import com.devp.forge.doc.MarkdownWriter;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.training.Bench.FailedRun;
import com.devp.forge.training.Bench.TaskScore;
import com.devp.forge.training.CoachBrief.Attempt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pieces without a database: file format, gate, what the coach reads, how its answer is read. */
class TrainingUnitTests {

	private final TrainingCodec codec = new TrainingCodec();

	private static final String FILE = """
			---
			id: rookie-learns
			version: 2
			name: Rookie lernt
			agent: rookie
			coach: coach
			train: [time-and-division, no-tool]
			holdout: [division]
			repetitions: 5
			max-steps: 4
			max-tokens: 20000
			approval: manual
			---
			Tools nutzen, wo sie helfen.
			""";

	@Test
	void parsesAndRoundTripsATraining() {
		Training t = codec.decode(MarkdownParser.parse(FILE, "t"));

		assertThat(t.train()).containsExactly("time-and-division", "no-tool");
		assertThat(t.holdout()).containsExactly("division");
		assertThat(t.allTasks()).containsExactly("time-and-division", "no-tool", "division");
		assertThat(t.repetitions()).isEqualTo(5);
		assertThat(t.maxSteps()).isEqualTo(4);
		assertThat(t.maxTokens()).isEqualTo(20000);
		assertThat(t.approval()).isEqualTo(Approval.MANUAL);
		assertThat(t.goal()).isEqualTo("Tools nutzen, wo sie helfen.");

		String written = MarkdownWriter.write(codec.frontMatter(t), codec.body(t));
		assertThat(codec.decode(MarkdownParser.parse(written, "t2"))).isEqualTo(t);
	}

	@Test
	void defaultsKeepAHandWrittenFileShort() {
		String minimal = """
				---
				id: x
				name: X
				agent: a
				coach: c
				train: t1
				---
				""";
		Training t = codec.decode(MarkdownParser.parse(minimal, "t"));
		assertThat(t.holdout()).isEmpty();
		assertThat(t.repetitions()).isEqualTo(3);
		assertThat(t.maxTokens()).isNull();
		assertThat(t.approval()).isEqualTo(Approval.GATE);
		assertThat(t.goal()).isNull();
	}

	@Test
	void refusesWhatWouldMakeTheMeasurementMeaningless() {
		assertThatThrownBy(() -> decode(FILE.replace("coach: coach", "coach: rookie")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("grader is never the student");
		assertThatThrownBy(() -> decode(FILE.replace("holdout: [division]", "holdout: [no-tool]")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("no-tool");
		assertThatThrownBy(() -> decode(FILE.replace("train: [time-and-division, no-tool]", "train: []")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("'train'");
		assertThatThrownBy(() -> decode(FILE.replace("repetitions: 5", "repetitions: 0")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("repetitions");
		assertThatThrownBy(() -> decode(FILE.replace("approval: manual", "approval: maybe")))
				.isInstanceOf(EntityInvalidException.class).hasMessageContaining("approval");
	}

	private Training decode(String text) {
		return codec.decode(MarkdownParser.parse(text, "t"));
	}

	@Test
	void gateWantsBetterTrainAndNoWorseHoldout() {
		Score best = new Score(1, 6, 2, 3);

		assertThat(Gate.decide(best, new Score(4, 6, 2, 3)).accepted()).isTrue();
		assertThat(Gate.decide(best, new Score(4, 6, 3, 3)).reason()).contains("1/6 -> 4/6").contains("2/3 -> 3/3");
		// A tie on train is noise, not progress.
		assertThat(Gate.decide(best, new Score(1, 6, 3, 3)).accepted()).isFalse();
		// Better on train, worse on holdout: fitted, not taught.
		Gate.Verdict fitted = Gate.decide(best, new Score(6, 6, 1, 3));
		assertThat(fitted.accepted()).isFalse();
		assertThat(fitted.reason()).contains("Holdout schlechter");
		// Without holdout only train counts.
		assertThat(Gate.decide(new Score(0, 3, 0, 0), new Score(1, 3, 0, 0)).accepted()).isTrue();
	}

	@Test
	void proposalTakesThePromptBetweenTheMarkersHeadingsAndAll() {
		String answer = """
				BEGRUENDUNG:
				- Tools verlangt: der Agent riet die Uhrzeit.
				PROMPT:
				<<<
				Du bist Rookie.

				# Arbeitsweise
				- Nutze Tools fuer Uhrzeit und Rechnungen.
				>>>
				""";
		Proposal p = Proposal.parse(answer, "m", 42, 10);

		assertThat(p.prompt()).startsWith("Du bist Rookie.").contains("# Arbeitsweise").endsWith("Rechnungen.");
		assertThat(p.rationale()).isEqualTo("- Tools verlangt: der Agent riet die Uhrzeit.");
		assertThat(p.tokens()).isEqualTo(42);

		assertThat(Proposal.parse("Ich wuerde den Prompt kuerzen.", "m", 1, 1).prompt()).isNull();
		assertThat(Proposal.parse("PROMPT:\n<<<\n\n>>>", "m", 1, 1).prompt()).isNull();
		assertThat(Proposal.parse(null, "m", null, 1).prompt()).isNull();
	}

	@Test
	void briefShowsTheEvidenceTheRejectedAttemptsAndTheFormat() {
		FailedRun failed = new FailedRun("time-and-division", UUID.randomUUID(), "DONE", "Wie spaet ist es?",
				"Etwa Mittag.", null, List.of(), List.of("tool-called: currentTime nicht aufgerufen"));
		CoachBrief brief = new CoachBrief(null, 3, "Du bist Rookie.", List.of("currentTime - Uhrzeit"),
				List.of(new TaskScore("time-and-division", false, 0, 3)), List.of(failed),
				List.of(new Attempt(4, "- kuerzer", "Train nicht besser: 0/3 gegen 0/3")));

		String text = brief.render();

		assertThat(text).contains("# Aktueller Systemprompt (v3)\n<<<\nDu bist Rookie.\n>>>");
		assertThat(text).contains("- time-and-division: 0/3 bestanden");
		assertThat(text).contains("Tool-Aufrufe: keine").contains("currentTime nicht aufgerufen");
		assertThat(text).contains("v4: - kuerzer -> Train nicht besser");
		assertThat(text).contains("Die Tasks zuverlaessiger bestehen.");
		assertThat(text).endsWith(">>>");
		assertThat(text).doesNotContain("Holdout");
	}
}
