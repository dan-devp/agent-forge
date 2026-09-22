package com.devp.forge.api;

import java.util.List;

import com.devp.forge.eval.Eval;
import com.devp.forge.eval.EvalParam;
import com.devp.forge.eval.EvalRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue of eval types with their parameter schema — the UI builds its forms from it. */
@RestController
@RequestMapping("/api/evals")
public class EvalController {

	private final EvalRegistry registry;

	public EvalController(EvalRegistry registry) {
		this.registry = registry;
	}

	@GetMapping
	public List<EvalType> list() {
		return registry.list().stream()
				.map(e -> new EvalType(e.type(), e.label(), e.description(), e.params()))
				.toList();
	}

	public record EvalType(String type, String label, String description, List<EvalParam> params) {
	}

	static String describe(Eval eval) {
		return eval.type() + " — " + eval.label();
	}
}
