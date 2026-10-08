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
			name: Rookie learns
			agent: rookie
			coach: coach
			train: [time-and-division, no-tool]
			holdout: [division]
			repetitions: 5
			max-steps: 4
			max-tokens: 20000
			approval: manual
			---
			Use tools where they help.
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
		assertThat(t.goal()).isEqualTo("Use tools where they help.");

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
		assertThat(fitted.reason()).contains("Holdout worse");
		// Without holdout only train counts.
		assertThat(Gate.decide(new Score(0, 3, 0, 0), new Score(1, 3, 0, 0)).accepted()).isTrue();
	}

	@Test
	void proposalTakesThePromptBetweenTheMarkersHeadingsAndAll() {
		String answer = """
				RATIONALE:
				- Tools required: the agent guessed the time.
				PROMPT:
				<<<
				You are Rookie.

				# How you work
				- Use tools for the time and for arithmetic.
				>>>
				""";
		Proposal p = Proposal.parse(answer, "m", 42, 10);

		assertThat(p.prompt()).startsWith("You are Rookie.").contains("# How you work").endsWith("arithmetic.");
		assertThat(p.rationale()).isEqualTo("- Tools required: the agent guessed the time.");
		assertThat(p.tokens()).isEqualTo(42);
		// A coach prompted in German before the switch still gets its rationale read.
		assertThat(Proposal.parse("BEGRUENDUNG:\n- kuerzer\nPROMPT:\n<<<\nx\n>>>", "m", 1, 1).rationale())
				.isEqualTo("- kuerzer");

		// A wrong closing marker or none at all still yields the prompt: the gate measures it anyway.
		assertThat(Proposal.parse("RATIONALE:\n- x\nPROMPT:\n<<<\nYou are Rookie.\n- Be brief.\n<<<", "m", 1, 1).prompt())
				.isEqualTo("You are Rookie.\n- Be brief.");
		assertThat(Proposal.parse("PROMPT:\n<<<\nYou are Rookie.\n", "m", 1, 1).prompt()).isEqualTo("You are Rookie.");

		assertThat(Proposal.parse("I would shorten the prompt.", "m", 1, 1).prompt()).isNull();
		assertThat(Proposal.parse("PROMPT:\n<<<\n\n>>>", "m", 1, 1).prompt()).isNull();
		assertThat(Proposal.parse(null, "m", null, 1).prompt()).isNull();
	}

	@Test
	void briefShowsTheEvidenceTheRejectedAttemptsAndTheFormat() {
		FailedRun failed = new FailedRun("time-and-division", UUID.randomUUID(), "DONE", "What time is it?",
				"About noon.", null, List.of(), List.of("tool-called: currentTime was not called"));
		CoachBrief brief = new CoachBrief(null, 3, "You are Rookie.", List.of("currentTime - the time"),
				List.of(new TaskScore("time-and-division", false, 0, 3)), List.of(failed),
				List.of(new Attempt(4, "- shorter", "Train not better: 0/3 vs 0/3")));

		String text = brief.render();

		assertThat(text).contains("# Current system prompt (v3)\n<<<\nYou are Rookie.\n>>>");
		assertThat(text).contains("- time-and-division: 0/3 passed");
		assertThat(text).contains("Tool calls: none").contains("currentTime was not called");
		assertThat(text).contains("v4: - shorter -> Train not better");
		assertThat(text).contains("Pass the tasks more reliably.");
		assertThat(text).contains("RATIONALE:");
		assertThat(text).endsWith(">>>");
		assertThat(text).doesNotContain("Holdout");
	}
}
