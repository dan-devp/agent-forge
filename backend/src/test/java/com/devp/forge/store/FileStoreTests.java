package com.devp.forge.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.devp.forge.model.Model;
import com.devp.forge.model.ModelCodec;
import com.devp.forge.store.StoreExceptions.EntityConflictException;
import com.devp.forge.store.StoreExceptions.EntityInvalidException;
import com.devp.forge.store.StoreExceptions.UnknownEntityException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The store with the model codec, on a fresh directory, without seeds. */
class FileStoreTests {

	@TempDir
	Path dir;

	private FileStore<Model> store;

	@BeforeEach
	void open() {
		store = new FileStore<>(dir, new ModelCodec(), false);
	}

	private static Model draft(String id) {
		return new Model(id, 0, "Name " + id, null, "http://localhost:1234/v1", "m", null, 0.5, true);
	}

	@Test
	void createWritesVersionOne() {
		Model saved = store.create(draft("alpha"));

		assertThat(saved.version()).isEqualTo(1);
		assertThat(dir.resolve("models/alpha/v1.md")).exists();
		assertThat(store.get("alpha")).isEqualTo(saved);
		assertThat(store.list()).containsExactly(saved);
	}

	@Test
	void updateWritesTheNextVersionAndLeavesTheOldFile() throws IOException {
		store.create(draft("alpha"));
		String v1 = Files.readString(dir.resolve("models/alpha/v1.md"));

		Model v2 = store.update(new Model("alpha", 0, "Renamed", null, "http://localhost:1234/v1", "m2", null, 0.5,
				true));

		assertThat(v2.version()).isEqualTo(2);
		assertThat(v2.name()).isEqualTo("Renamed");
		assertThat(Files.readString(dir.resolve("models/alpha/v1.md"))).isEqualTo(v1);
		assertThat(store.get("alpha").version()).isEqualTo(2);
		assertThat(store.get("alpha", 1).name()).isEqualTo("Name alpha");
		assertThat(store.versions("alpha")).extracting(Model::version).containsExactly(1, 2);
	}

	@Test
	void createOnExistingIdIsAConflict() {
		store.create(draft("alpha"));
		assertThatThrownBy(() -> store.create(draft("alpha"))).isInstanceOf(EntityConflictException.class);
	}

	@Test
	void updateOnUnknownIdIsNotFound() {
		assertThatThrownBy(() -> store.update(draft("ghost"))).isInstanceOf(UnknownEntityException.class);
	}

	@Test
	void rejectsBadIds() {
		assertThatThrownBy(() -> store.create(draft("Alpha"))).isInstanceOf(EntityInvalidException.class);
		assertThatThrownBy(() -> store.create(draft("a b"))).isInstanceOf(EntityInvalidException.class);
		assertThatThrownBy(() -> store.create(draft("-a"))).isInstanceOf(EntityInvalidException.class);
	}

	/** Validation runs through the codec on the way in — the file is never written. */
	@Test
	void rejectsInvalidEntitiesBeforeWriting() {
		Model noName = new Model("alpha", 0, null, null, "http://h/v1", "m", null, 0.5, true);
		assertThatThrownBy(() -> store.create(noName)).isInstanceOf(EntityInvalidException.class)
				.hasMessageContaining("name");
		assertThat(dir.resolve("models/alpha")).doesNotExist();
	}

	@Test
	void archiveFreesTheIdAndKeepsTheFiles() throws IOException {
		store.create(draft("alpha"));
		store.update(draft("alpha"));

		store.archive("alpha");

		assertThat(store.exists("alpha")).isFalse();
		assertThat(dir.resolve("models/alpha")).doesNotExist();
		try (var archived = Files.list(dir.resolve("models/_archive"))) {
			Path moved = archived.findFirst().orElseThrow();
			assertThat(moved.getFileName().toString()).startsWith("alpha-");
			assertThat(moved.resolve("v2.md")).exists();
		}
		// Free again.
		assertThat(store.create(draft("alpha")).version()).isEqualTo(1);
	}

	@Test
	void reloadReadsWhatIsOnDisk() throws IOException {
		Path file = dir.resolve("models/beta/v1.md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
				---
				id: beta
				version: 1
				name: Beta
				base-url: http://localhost:1234/v1
				model: b
				local: true
				---
				""");

		store.reload();

		assertThat(store.get("beta").name()).isEqualTo("Beta");
		assertThat(store.get("beta").temperature()).isEqualTo(0.7);
	}

	/** A broken file is skipped with a warning; the rest still loads. */
	@Test
	void skipsFilesThatDoNotMatchTheirPlace() throws IOException {
		store.create(draft("alpha"));
		Path wrong = dir.resolve("models/gamma/v1.md");
		Files.createDirectories(wrong.getParent());
		Files.writeString(wrong, "---\nid: delta\nversion: 1\nname: D\nbase-url: http://h/v1\nmodel: d\n---\n");

		store.reload();

		assertThat(store.exists("alpha")).isTrue();
		assertThat(store.exists("gamma")).isFalse();
		assertThat(store.exists("delta")).isFalse();
	}

	@Test
	void notifiesListenersOnChange() {
		int[] calls = {0};
		store.onChange(() -> calls[0]++);

		store.create(draft("alpha"));
		store.update(draft("alpha"));
		store.archive("alpha");

		assertThat(calls[0]).isEqualTo(3);
	}
}
